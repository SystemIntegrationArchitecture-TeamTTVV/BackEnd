package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendRequestDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.FriendRequest;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final FriendRepository friendRepository;
    private final AuthServiceClient authServiceClient;
    private final SocketService socketService;
    private final NotificationService notificationService;

    public List<FriendRequestDTO> getFriendRequestsBySenderId(String senderId) {
        return friendRequestRepository.findBySenderId(senderId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<FriendRequestDTO> getFriendRequestsByReceiverId(String receiverId) {
        return friendRequestRepository.findByReceiverId(receiverId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<FriendRequestDTO> getPendingFriendRequestsByReceiverId(String receiverId) {
        return friendRequestRepository.findByReceiverIdAndStatus(receiverId, "PENDING").stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public FriendRequestDTO getFriendRequestById(String id) {
        return friendRequestRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));
    }

    public FriendRequestDTO createFriendRequest(FriendRequestDTO friendRequestDTO) {
        // Check if pending request already exists
        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId(), "PENDING")) {
            throw new RuntimeException("Friend request already exists");
        }
        
        // Check if reverse pending request exists (receiver sent to sender)
        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(
                friendRequestDTO.getReceiverId(), friendRequestDTO.getSenderId(), "PENDING")) {
            throw new RuntimeException("Friend request already exists");
        }

        // Check if already friends (check FriendRequest ACTIVE or Friend entity)
        Optional<FriendRequest> existingAccepted = friendRequestRepository.findBySenderIdAndReceiverId(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId())
                .filter(fr -> "ACTIVE".equals(fr.getStatus()));
        
        Optional<FriendRequest> reverseAccepted = friendRequestRepository.findBySenderIdAndReceiverId(
                friendRequestDTO.getReceiverId(), friendRequestDTO.getSenderId())
                .filter(fr -> "ACTIVE".equals(fr.getStatus()));
        
        if (existingAccepted.isPresent() || reverseAccepted.isPresent() ||
            friendRepository.existsByUserIdAndFriendId(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId()) ||
            friendRepository.existsByUserIdAndFriendId(
                friendRequestDTO.getReceiverId(), friendRequestDTO.getSenderId())) {
            throw new RuntimeException("Users are already friends");
        }

        // Xóa bất kỳ friend request cũ nào có cùng sender/receiver (nếu có)
        // Để tránh duplicate key error do unique index
        friendRequestRepository.findBySenderIdAndReceiverId(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId())
                .ifPresent(friendRequestRepository::delete);
        
        friendRequestRepository.findBySenderIdAndReceiverId(
                friendRequestDTO.getReceiverId(), friendRequestDTO.getSenderId())
                .ifPresent(friendRequestRepository::delete);

        FriendRequest friendRequest = toEntity(friendRequestDTO);
        friendRequest.setStatus("PENDING");
        friendRequest.setCreatedAt(LocalDateTime.now());
        friendRequest.setUpdatedAt(LocalDateTime.now());
        FriendRequest saved = friendRequestRepository.save(friendRequest);
        FriendRequestDTO savedDTO = toDTO(saved);
        
        // Send notification to receiver via socket
        UserDTO sender;
        try {
            sender = authServiceClient.getUserById(savedDTO.getSenderId());
        } catch (Exception e) {
            throw new RuntimeException("Sender not found");
        }
        
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(savedDTO.getReceiverId());
        notification.setActorId(savedDTO.getSenderId());
        notification.setActorName(savedDTO.getSenderName());
        notification.setActorAvatar(savedDTO.getSenderAvatar());
        notification.setType("FRIEND_REQUEST");
        notification.setTitle("Friend Request");
        notification.setContent((sender.getFullName() != null ? sender.getFullName() : sender.getUsername()) + " sent you a friend request");
        notification.setRelatedId(savedDTO.getId());
        notification.setRelatedType("FRIEND_REQUEST");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        notificationService.createNotification(notification);
        
        // Send socket event
        socketService.sendNotification(
            savedDTO.getReceiverId(),
            SocketEventDTO.notification(savedDTO.getReceiverId(), notification)
        );
        
        return savedDTO;
    }

    public FriendRequestDTO acceptFriendRequest(String id) {
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));

        if (!"PENDING".equals(friendRequest.getStatus())) {
            throw new RuntimeException("Friend request is not pending");
        }

        friendRequest.setStatus("ACTIVE");
        friendRequest.setUpdatedAt(LocalDateTime.now());
        friendRequestRepository.save(friendRequest);

        // Create friendship (bidirectional)
        UserDTO sender;
        UserDTO receiver;
        try {
            sender = authServiceClient.getUserById(friendRequest.getSenderId());
            receiver = authServiceClient.getUserById(friendRequest.getReceiverId());
        } catch (Exception e) {
            throw new RuntimeException("User not found");
        }

        // Create friend relationship from sender to receiver
        Friend friend1 = new Friend();
        friend1.setUserId(sender.getId());
        friend1.setFriendId(receiver.getId());
        friend1.setCreatedAt(LocalDateTime.now());
        friend1.setUpdatedAt(LocalDateTime.now());
        friendRepository.save(friend1);

        // Create friend relationship from receiver to sender
        Friend friend2 = new Friend();
        friend2.setUserId(receiver.getId());
        friend2.setFriendId(sender.getId());
        friend2.setCreatedAt(LocalDateTime.now());
        friend2.setUpdatedAt(LocalDateTime.now());
        friendRepository.save(friend2);

        FriendRequestDTO friendRequestDTO = toDTO(friendRequest);
        
        // Send notification to sender via socket
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(friendRequestDTO.getSenderId());
        notification.setActorId(friendRequestDTO.getReceiverId());
        notification.setActorName(friendRequestDTO.getReceiverName());
        notification.setActorAvatar(friendRequestDTO.getReceiverAvatar());
        notification.setType("FRIEND_ACCEPTED");
        notification.setTitle("Friend Request Accepted");
        notification.setContent((receiver.getFullName() != null ? receiver.getFullName() : receiver.getUsername()) + " accepted your friend request");
        notification.setRelatedId(friendRequestDTO.getId());
        notification.setRelatedType("FRIEND_REQUEST");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        NotificationDTO savedNotification = notificationService.createNotification(notification);
        log.info("📨 Created notification: id={}, recipientId={}, type={}", 
            savedNotification.getId(), savedNotification.getRecipientId(), savedNotification.getType());
        
        // Xóa notification FRIEND_REQUEST của receiver (vì đã accept rồi)
        // Xóa notification của receiver cụ thể với relatedId = friendRequestId
        notificationService.deleteNotificationByRecipientAndRelatedIdAndType(
            friendRequestDTO.getReceiverId(), 
            friendRequestDTO.getId(), 
            "FRIEND_REQUEST"
        );
        log.info("🗑️ Deleted FRIEND_REQUEST notification for receiver {} after acceptance: relatedId={}", 
            friendRequestDTO.getReceiverId(), friendRequestDTO.getId());
        
        // Get sender username for WebSocket (convertAndSendToUser uses username, not userId)
        // Note: sender was already loaded above, but we need username for WebSocket
        String senderUsername = sender.getUsername();
        
        // Send socket event
        SocketEventDTO socketEvent = SocketEventDTO.notification(friendRequestDTO.getSenderId(), savedNotification);
        log.info("📤 Sending socket notification to user {} (username={}): {}", 
            friendRequestDTO.getSenderId(), senderUsername, socketEvent.getType());
        socketService.sendNotification(senderUsername, socketEvent);
        
        return friendRequestDTO;
    }

    public void rejectFriendRequest(String id) {
        // Xóa friend request thay vì set status REJECTED
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));
        
        // Save info before deleting
        String senderId = friendRequest.getSenderId();
        String receiverId = friendRequest.getReceiverId();
        UserDTO receiver;
        UserDTO sender;
        try {
            receiver = authServiceClient.getUserById(receiverId);
            sender = authServiceClient.getUserById(senderId);
        } catch (Exception e) {
            throw new RuntimeException("User not found");
        }
        
        friendRequestRepository.delete(friendRequest);
        log.info("🗑️ Rejected (deleted) friend request: id={}", id);
        
        // Send socket notification to sender (transient, not persisted)
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(senderId);
        notification.setActorId(receiverId);
        notification.setActorName(receiver.getFullName() != null ? receiver.getFullName() : receiver.getUsername());
        notification.setActorAvatar(receiver.getAvatar());
        notification.setType("FRIEND_REJECTED");
        notification.setTitle("Friend Request Rejected");
        notification.setContent((receiver.getFullName() != null ? receiver.getFullName() : receiver.getUsername()) + " đã từ chối lời mời kết bạn của bạn");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        socketService.sendNotification(
            sender.getUsername(),
            SocketEventDTO.notification(senderId, notification)
        );
        log.info("📤 Sent FRIEND_REJECTED socket to sender {} (username={})", senderId, sender.getUsername());
        
        // Delete the FRIEND_REQUEST notification from DB
        notificationService.deleteNotificationByRecipientAndRelatedIdAndType(
            receiverId, id, "FRIEND_REQUEST"
        );
    }

    public void cancelFriendRequest(String id) {
        // Xóa friend request thay vì set status CANCELLED
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));
        
        String senderId = friendRequest.getSenderId();
        String receiverId = friendRequest.getReceiverId();
        UserDTO senderUser;
        UserDTO receiverUser;
        try {
            senderUser = authServiceClient.getUserById(senderId);
            receiverUser = authServiceClient.getUserById(receiverId);
        } catch (Exception e) {
            throw new RuntimeException("User not found");
        }
        
        friendRequestRepository.delete(friendRequest);
        log.info("🗑️ Cancelled (deleted) friend request: id={}", id);
        
        // Send socket notification to receiver (transient, not persisted)
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(receiverId);
        notification.setActorId(senderId);
        notification.setActorName(senderUser.getFullName() != null ? senderUser.getFullName() : senderUser.getUsername());
        notification.setActorAvatar(senderUser.getAvatar());
        notification.setType("FRIEND_CANCELLED");
        notification.setTitle("Friend Request Cancelled");
        notification.setContent((senderUser.getFullName() != null ? senderUser.getFullName() : senderUser.getUsername()) + " đã thu hồi lời mời kết bạn");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        socketService.sendNotification(
            receiverUser.getUsername(),
            SocketEventDTO.notification(receiverId, notification)
        );
        log.info("📤 Sent FRIEND_CANCELLED socket to receiver {} (username={})", receiverId, receiverUser.getUsername());
        
        // Delete the FRIEND_REQUEST notification from DB
        notificationService.deleteNotificationByRecipientAndRelatedIdAndType(
            receiverId, id, "FRIEND_REQUEST"
        );
    }
    
    /**
     * Xóa bạn (unfriend) - Xóa FriendRequest ACTIVE và Friend entities
     */
    public void unfriend(String userId1, String userId2) {
        // Xóa FriendRequest ACTIVE (cả 2 chiều)
        List<FriendRequest> friendRequests = friendRequestRepository.findBySenderId(userId1);
        friendRequests.addAll(friendRequestRepository.findBySenderId(userId2));
        
        friendRequests.stream()
                .filter(fr -> ("ACTIVE".equals(fr.getStatus())) &&
                             ((fr.getSenderId().equals(userId1) && fr.getReceiverId().equals(userId2)) ||
                              (fr.getSenderId().equals(userId2) && fr.getReceiverId().equals(userId1))))
                .forEach(fr -> {
                    friendRequestRepository.delete(fr);
                    log.info("🗑️ Deleted friend request: id={}, sender={}, receiver={}", 
                        fr.getId(), fr.getSenderId(), fr.getReceiverId());
                });
        
        // Xóa Friend entities (cả 2 chiều)
        friendRepository.findByUserIdAndFriendId(userId1, userId2)
                .ifPresent(friend -> {
                    friendRepository.delete(friend);
                    log.info("🗑️ Deleted friend relationship: userId={}, friendId={}", userId1, userId2);
                });
        
        friendRepository.findByUserIdAndFriendId(userId2, userId1)
                .ifPresent(friend -> {
                    friendRepository.delete(friend);
                    log.info("🗑️ Deleted friend relationship: userId={}, friendId={}", userId2, userId1);
                });
        
        log.info("✅ Unfriended: user1={}, user2={}", userId1, userId2);
        
        // Send socket notification to user2 (the other user)
        UserDTO user1;
        UserDTO user2;
        try {
            user1 = authServiceClient.getUserById(userId1);
            user2 = authServiceClient.getUserById(userId2);
        } catch (Exception e) {
            throw new RuntimeException("User not found");
        }
        
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(userId2);
        notification.setActorId(userId1);
        notification.setActorName(user1.getFullName() != null ? user1.getFullName() : user1.getUsername());
        notification.setActorAvatar(user1.getAvatar());
        notification.setType("FRIEND_REMOVED");
        notification.setTitle("Friend Removed");
        notification.setContent((user1.getFullName() != null ? user1.getFullName() : user1.getUsername()) + " đã xóa bạn khỏi danh sách bạn bè");
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        socketService.sendNotification(
            user2.getUsername(),
            SocketEventDTO.notification(userId2, notification)
        );
        log.info("📤 Sent FRIEND_REMOVED socket to user {} (username={})", userId2, user2.getUsername());
    }

    public void deleteFriendRequest(String id) {
        friendRequestRepository.deleteById(id);
    }

    private FriendRequestDTO toDTO(FriendRequest friendRequest) {
        FriendRequestDTO dto = new FriendRequestDTO();
        dto.setId(friendRequest.getId());
        dto.setSenderId(friendRequest.getSenderId());
        if (friendRequest.getSenderId() != null) {
            try {
                UserDTO sender = authServiceClient.getUserById(friendRequest.getSenderId());
                dto.setSenderName(sender.getFullName() != null ? sender.getFullName() : sender.getUsername());
                dto.setSenderAvatar(sender.getAvatar());
            } catch (Exception e) {
                dto.setSenderName("Unknown");
            }
        }
        dto.setReceiverId(friendRequest.getReceiverId());
        if (friendRequest.getReceiverId() != null) {
            try {
                UserDTO receiver = authServiceClient.getUserById(friendRequest.getReceiverId());
                dto.setReceiverName(receiver.getFullName() != null ? receiver.getFullName() : receiver.getUsername());
                dto.setReceiverAvatar(receiver.getAvatar());
            } catch (Exception e) {
                dto.setReceiverName("Unknown");
            }
        }
        dto.setStatus(friendRequest.getStatus());
        dto.setCreatedAt(friendRequest.getCreatedAt());
        dto.setUpdatedAt(friendRequest.getUpdatedAt());
        return dto;
    }

    private FriendRequest toEntity(FriendRequestDTO dto) {
        FriendRequest friendRequest = new FriendRequest();
        if (dto.getSenderId() != null) {
            friendRequest.setSenderId(dto.getSenderId());
        }
        if (dto.getReceiverId() != null) {
            friendRequest.setReceiverId(dto.getReceiverId());
        }
        return friendRequest;
    }
}

