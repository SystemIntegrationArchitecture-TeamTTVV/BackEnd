package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@CompoundIndexes({
    @CompoundIndex(name = "recipient_created", def = "{'recipientId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "recipient_read_created", def = "{'recipientId': 1, 'isRead': 1, 'createdAt': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private String id;
    
    private String recipientId;
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

