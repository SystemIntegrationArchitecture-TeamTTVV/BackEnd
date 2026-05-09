package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.NotificationDTO;
import edu.iuh.fit.se.commonservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/recipient/{recipientId}")
    public ResponseEntity<List<NotificationDTO>> getNotificationsByRecipientId(@PathVariable String recipientId) {
        return ResponseEntity.ok(notificationService.getNotificationsByRecipientId(recipientId));
    }

    @GetMapping("/recipient/{recipientId}/unread")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotificationsByRecipientId(@PathVariable String recipientId) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationsByRecipientId(recipientId));
    }

    @GetMapping("/recipient/{recipientId}/unread/count")
    public ResponseEntity<Long> getUnreadNotificationCount(@PathVariable String recipientId) {
        return ResponseEntity.ok(notificationService.getUnreadNotificationCount(recipientId));
    }

    /**
     * Filter notifications by one or more comma-separated types, e.g.:
     * GET /recipient/{id}/filter?type=FRIEND_REQUEST,FRIEND_ACCEPTED
     * GET /recipient/{id}/filter?type=LIKE_POST
     * Omit ?type to return all.
     */
    @GetMapping("/recipient/{recipientId}/filter")
    public ResponseEntity<List<NotificationDTO>> getNotificationsByType(
            @PathVariable String recipientId,
            @RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) {
            return ResponseEntity.ok(notificationService.getNotificationsByRecipientId(recipientId));
        }
        List<String> types = Arrays.stream(type.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (types.size() == 1) {
            return ResponseEntity.ok(notificationService.getNotificationsByRecipientIdAndType(recipientId, types.get(0)));
        }
        return ResponseEntity.ok(notificationService.getNotificationsByRecipientIdAndTypes(recipientId, types));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> getNotificationById(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.getNotificationById(id));
    }

    @PostMapping
    public ResponseEntity<NotificationDTO> createNotification(@RequestBody NotificationDTO notificationDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.createNotification(notificationDTO));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markAsRead(@PathVariable String id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    @PutMapping("/recipient/{recipientId}/read-all")
    public ResponseEntity<Void> markAllAsRead(@PathVariable String recipientId) {
        notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/recipient/{recipientId}")
    public ResponseEntity<Void> deleteAllNotificationsByRecipientId(@PathVariable String recipientId) {
        notificationService.deleteAllNotificationsByRecipientId(recipientId);
        return ResponseEntity.noContent().build();
    }
}

