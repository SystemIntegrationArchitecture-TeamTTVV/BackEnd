package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendRequestDTO;
import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.FriendRequest;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.FriendRequestRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
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
    private final UserRepository userRepository;
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
        User sender = userRepository.findById(savedDTO.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        
        NotificationDTO notification = new NotificationDTO();
        notification.setRecipientId(savedDTO.getReceiverId());
        notification.setActorId(savedDTO.getSenderId());
        notification.setActorName(savedDTO.getSenderName());
        notification.setActorAvatar(savedDTO.getSenderAvatar());
        notification.setType("FRIEND_REQUEST");
        notification.setTitle("Friend Request");
        notification.setContent(sender.getFullName() + " sent you a friend request");
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
        User sender = userRepository.findById(friendRequest.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findById(friendRequest.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // Create friend relationship from sender to receiver
        Friend friend1 = new Friend();
        friend1.setUser(sender);
        friend1.setUserId(sender.getId());
        friend1.setFriend(receiver);
        friend1.setFriendId(receiver.getId());
        friend1.setCreatedAt(LocalDateTime.now());
        friend1.setUpdatedAt(LocalDateTime.now());
        friendRepository.save(friend1);

        // Create friend relationship from receiver to sender
        Friend friend2 = new Friend();
        friend2.setUser(receiver);
        friend2.setUserId(receiver.getId());
        friend2.setFriend(sender);
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
        notification.setContent(receiver.getFullName() + " accepted your friend request");
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
        
        friendRequestRepository.delete(friendRequest);
        log.info("🗑️ Rejected (deleted) friend request: id={}", id);
    }

    public void cancelFriendRequest(String id) {
        // Xóa friend request thay vì set status CANCELLED
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));
        
        friendRequestRepository.delete(friendRequest);
        log.info("🗑️ Cancelled (deleted) friend request: id={}", id);
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
    }

    public void deleteFriendRequest(String id) {
        friendRequestRepository.deleteById(id);
    }

    private FriendRequestDTO toDTO(FriendRequest friendRequest) {
        FriendRequestDTO dto = new FriendRequestDTO();
        dto.setId(friendRequest.getId());
        dto.setSenderId(friendRequest.getSenderId());
        if (friendRequest.getSender() != null) {
            dto.setSenderName(friendRequest.getSender().getFullName());
            dto.setSenderAvatar(friendRequest.getSender().getAvatar());
        }
        dto.setReceiverId(friendRequest.getReceiverId());
        if (friendRequest.getReceiver() != null) {
            dto.setReceiverName(friendRequest.getReceiver().getFullName());
            dto.setReceiverAvatar(friendRequest.getReceiver().getAvatar());
        }
        dto.setStatus(friendRequest.getStatus());
        dto.setCreatedAt(friendRequest.getCreatedAt());
        dto.setUpdatedAt(friendRequest.getUpdatedAt());
        return dto;
    }

    private FriendRequest toEntity(FriendRequestDTO dto) {
        FriendRequest friendRequest = new FriendRequest();
        if (dto.getSenderId() != null) {
            User sender = userRepository.findById(dto.getSenderId())
                    .orElseThrow(() -> new RuntimeException("Sender not found"));
            friendRequest.setSender(sender);
            friendRequest.setSenderId(dto.getSenderId());
        }
        if (dto.getReceiverId() != null) {
            User receiver = userRepository.findById(dto.getReceiverId())
                    .orElseThrow(() -> new RuntimeException("Receiver not found"));
            friendRequest.setReceiver(receiver);
            friendRequest.setReceiverId(dto.getReceiverId());
        }
        return friendRequest;
    }
}

