package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private String id;
    
    @DBRef
    private User recipient; // Người nhận thông báo
    private String recipientId;
    
    @DBRef
    private User actor; // Người thực hiện hành động
    private String actorId;
    
    private String type; // LIKE_POST, COMMENT_POST, FRIEND_REQUEST, FRIEND_ACCEPTED, GROUP_INVITE, EVENT_INVITE, etc.
    private String title; // Tiêu đề thông báo
    private String content; // Nội dung thông báo
    private String image; // URL ảnh
    
    // Reference đến object liên quan
    private String relatedId; // ID của post, comment, group, event, etc.
    private String relatedType; // POST, COMMENT, GROUP, EVENT, etc.
    
    private boolean isRead = false;
    private LocalDateTime createdAt;
}

