package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Notification;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// Burst dedupe window: identical (recipient, actor, type, relatedId) within this many seconds
// is silently dropped to prevent spam notifications.
// Value must be kept short enough not to suppress legitimate repeated actions (e.g. 30 s).

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEDUPE_SECONDS = 30;

    private final NotificationRepository notificationRepository;
    private final AuthServiceClient authServiceClient;
    private final SocketService socketService;

    public List<NotificationDTO> getNotificationsByRecipientId(String recipientId) {
        return toDTOs(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId));
    }

    public List<NotificationDTO> getUnreadNotificationsByRecipientId(String recipientId) {
        return toDTOs(notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId));
    }

    public long getUnreadNotificationCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    public NotificationDTO getNotificationById(String id) {
        return notificationRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
    }

    public List<NotificationDTO> getNotificationsByRecipientIdAndType(String recipientId, String type) {
        return toDTOs(notificationRepository.findByRecipientIdAndTypeOrderByCreatedAtDesc(recipientId, type));
    }

    public List<NotificationDTO> getNotificationsByRecipientIdAndTypes(String recipientId, List<String> types) {
        return toDTOs(notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(recipientId, types));
    }

    public NotificationDTO createNotification(NotificationDTO notificationDTO) {
        // Burst dedupe: drop identical notification within DEDUPE_SECONDS window
        if (notificationDTO.getRecipientId() != null
                && notificationDTO.getActorId() != null
                && notificationDTO.getType() != null
                && notificationDTO.getRelatedId() != null) {
            LocalDateTime dedupeWindow = LocalDateTime.now().minusSeconds(DEDUPE_SECONDS);
            boolean isDuplicate = notificationRepository.existsByRecipientIdAndActorIdAndTypeAndRelatedIdAndCreatedAtAfter(
                    notificationDTO.getRecipientId(),
                    notificationDTO.getActorId(),
                    notificationDTO.getType(),
                    notificationDTO.getRelatedId(),
                    dedupeWindow);
            if (isDuplicate) {
                // Return a dummy DTO with no id so callers don't break; nothing is persisted
                return notificationDTO;
            }
        }

        Notification notification = toEntity(notificationDTO);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        NotificationDTO savedDTO = toDTO(saved);
        
        // Send socket event to recipient using username, not userId
        if (savedDTO.getRecipientId() != null) {
            // Fetch recipient user to get username
            try {
                UserDTO recipient = authServiceClient.getUserById(savedDTO.getRecipientId());
                if (recipient != null && recipient.getUsername() != null) {
                    socketService.sendNotification(
                        recipient.getUsername(), // Use username, not userId
                        SocketEventDTO.notification(savedDTO.getRecipientId(), savedDTO)
                    );
                }
            } catch (Exception e) {}
        }
        
        return savedDTO;
    }

    public NotificationDTO markAsRead(String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
        notification.setRead(true);
        Notification saved = notificationRepository.save(notification);
        return toDTO(saved);
    }

    public void markAllAsRead(String recipientId) {
        List<Notification> notifications = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    public void deleteNotification(String id) {
        notificationRepository.deleteById(id);
    }

    public void deleteAllNotificationsByRecipientId(String recipientId) {
        notificationRepository.deleteByRecipientId(recipientId);
    }

    public void deleteNotificationsByRelatedIdAndType(String relatedId, String type) {
        notificationRepository.deleteByRelatedIdAndType(relatedId, type);
    }

    public void deleteNotificationByRecipientAndRelatedIdAndType(String recipientId, String relatedId, String type) {
        notificationRepository.deleteByRecipientIdAndRelatedIdAndType(recipientId, relatedId, type);
    }

    private NotificationDTO toDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setRecipientId(notification.getRecipientId());
        if (notification.getRecipientId() != null) {
            try {
                UserDTO recipient = authServiceClient.getUserById(notification.getRecipientId());
                dto.setRecipientName(recipient.getFullName() != null ? recipient.getFullName() : recipient.getUsername());
            } catch (Exception e) {
                dto.setRecipientName("Unknown");
            }
        }
        dto.setActorId(notification.getActorId());
        if (notification.getActorId() != null) {
            try {
                UserDTO actor = authServiceClient.getUserById(notification.getActorId());
                dto.setActorName(actor.getFullName() != null ? actor.getFullName() : actor.getUsername());
                dto.setActorAvatar(actor.getAvatar());
            } catch (Exception e) {
                dto.setActorName("Unknown");
            }
        }
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setContent(notification.getContent());
        dto.setImage(notification.getImage());
        dto.setRelatedId(notification.getRelatedId());
        dto.setRelatedType(notification.getRelatedType());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }

    private Notification toEntity(NotificationDTO dto) {
        Notification notification = new Notification();
        if (dto.getRecipientId() != null) {
            notification.setRecipientId(dto.getRecipientId());
        }
        if (dto.getActorId() != null) {
            notification.setActorId(dto.getActorId());
        }
        notification.setType(dto.getType());
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setImage(dto.getImage());
        notification.setRelatedId(dto.getRelatedId());
        notification.setRelatedType(dto.getRelatedType());
        return notification;
    }

    private List<NotificationDTO> toDTOs(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.Set<String> userIds = notifications.stream()
                .flatMap(n -> java.util.stream.Stream.of(n.getRecipientId(), n.getActorId()))
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        java.util.Map<String, UserDTO> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                List<UserDTO> users = authServiceClient.batchLookup(new java.util.ArrayList<>(userIds));
                if (users != null) {
                    for (UserDTO user : users) {
                        if (user != null && user.getId() != null) {
                            userMap.put(user.getId(), user);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error in batch lookup users: {}", e.getMessage());
            }
        }

        return notifications.stream()
                .map(n -> toDTOWithCache(n, userMap))
                .collect(Collectors.toList());
    }

    private NotificationDTO toDTOWithCache(Notification notification, java.util.Map<String, UserDTO> userMap) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setRecipientId(notification.getRecipientId());
        if (notification.getRecipientId() != null) {
            UserDTO recipient = userMap.get(notification.getRecipientId());
            if (recipient != null) {
                dto.setRecipientName(recipient.getFullName() != null ? recipient.getFullName() : recipient.getUsername());
            } else {
                dto.setRecipientName("Unknown");
            }
        }
        dto.setActorId(notification.getActorId());
        if (notification.getActorId() != null) {
            UserDTO actor = userMap.get(notification.getActorId());
            if (actor != null) {
                dto.setActorName(actor.getFullName() != null ? actor.getFullName() : actor.getUsername());
                dto.setActorAvatar(actor.getAvatar());
            } else {
                dto.setActorName("Unknown");
            }
        }
        dto.setType(notification.getType());
        dto.setTitle(notification.getTitle());
        dto.setContent(notification.getContent());
        dto.setImage(notification.getImage());
        dto.setRelatedId(notification.getRelatedId());
        dto.setRelatedType(notification.getRelatedType());
        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());
        return dto;
    }
}

