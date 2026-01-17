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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        // Check if request already exists
        if (friendRequestRepository.existsBySenderIdAndReceiverId(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId())) {
            throw new RuntimeException("Friend request already exists");
        }

        // Check if already friends
        if (friendRepository.existsByUserIdAndFriendId(
                friendRequestDTO.getSenderId(), friendRequestDTO.getReceiverId()) ||
            friendRepository.existsByUserIdAndFriendId(
                friendRequestDTO.getReceiverId(), friendRequestDTO.getSenderId())) {
            throw new RuntimeException("Users are already friends");
        }

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

        friendRequest.setStatus("ACCEPTED");
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
        
        notificationService.createNotification(notification);
        
        // Send socket event
        socketService.sendNotification(
            friendRequestDTO.getSenderId(),
            SocketEventDTO.notification(friendRequestDTO.getSenderId(), notification)
        );
        
        return friendRequestDTO;
    }

    public FriendRequestDTO rejectFriendRequest(String id) {
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));

        friendRequest.setStatus("REJECTED");
        friendRequest.setUpdatedAt(LocalDateTime.now());
        FriendRequest saved = friendRequestRepository.save(friendRequest);
        return toDTO(saved);
    }

    public FriendRequestDTO cancelFriendRequest(String id) {
        FriendRequest friendRequest = friendRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Friend request not found with id: " + id));

        friendRequest.setStatus("CANCELLED");
        friendRequest.setUpdatedAt(LocalDateTime.now());
        FriendRequest saved = friendRequestRepository.save(friendRequest);
        return toDTO(saved);
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

