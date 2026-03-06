package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.Notification;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.NotificationRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
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
            userRepository.findById(savedDTO.getRecipientId()).ifPresent(recipient -> {
                socketService.sendNotification(
                    recipient.getUsername(), // Use username, not userId
                    SocketEventDTO.notification(savedDTO.getRecipientId(), savedDTO)
                );
            });
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
        if (notification.getRecipient() != null) {
            dto.setRecipientName(notification.getRecipient().getFullName());
        }
        dto.setActorId(notification.getActorId());
        if (notification.getActor() != null) {
            dto.setActorName(notification.getActor().getFullName());
            dto.setActorAvatar(notification.getActor().getAvatar());
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
            User recipient = userRepository.findById(dto.getRecipientId())
                    .orElseThrow(() -> new RuntimeException("Recipient not found"));
            notification.setRecipient(recipient);
            notification.setRecipientId(dto.getRecipientId());
        }
        if (dto.getActorId() != null) {
            User actor = userRepository.findById(dto.getActorId())
                    .orElseThrow(() -> new RuntimeException("Actor not found"));
            notification.setActor(actor);
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

