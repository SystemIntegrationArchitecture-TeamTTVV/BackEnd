package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private String id;
    private String recipientId;
    private String recipientName;
    private String actorId;
    private String actorName;
    private String actorAvatar;
    private String type; // LIKE_POST, COMMENT_POST, FRIEND_REQUEST, FRIEND_ACCEPTED, GROUP_INVITE, EVENT_INVITE, etc.
    private String title;
    private String content;
    private String image;
    private String relatedId;
    private String relatedType; // POST, COMMENT, GROUP, EVENT, etc.
    private boolean isRead;
    private LocalDateTime createdAt;
}

