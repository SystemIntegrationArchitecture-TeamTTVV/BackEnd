package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Notification;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthServiceClient authServiceClient;
    private final SocketService socketService;

    public List<NotificationDTO> getNotificationsByRecipientId(String recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<NotificationDTO> getUnreadNotificationsByRecipientId(String recipientId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long getUnreadNotificationCount(String recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    public NotificationDTO getNotificationById(String id) {
        return notificationRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + id));
    }

    public NotificationDTO createNotification(NotificationDTO notificationDTO) {
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
}

