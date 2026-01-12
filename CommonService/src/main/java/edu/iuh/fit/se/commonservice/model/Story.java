package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "stories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Story {
    @Id
    private String id;
    
    @DBRef
    private User author;
    
    private String type = "IMAGE"; // IMAGE, VIDEO
    private String mediaUrl; // URL ảnh hoặc video
    private String thumbnailUrl; // URL thumbnail cho video
    
    private String text; // Text overlay trên story
    private String backgroundColor; // Màu nền cho text
    
    private String visibility = "PUBLIC"; // PUBLIC, FRIENDS, CUSTOM
    
    private int viewCount = 0;
    private int reactionCount = 0;
    
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt; // Story tự động xóa sau 24h
    private boolean isActive = true;
}

