package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryDTO {
    private String id;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private String type; // IMAGE, VIDEO
    private String mediaUrl;
    private String thumbnailUrl;
    private String text;
    private String backgroundColor;
    private String visibility; // PUBLIC, FRIENDS, CUSTOM
    private int viewCount;
    private int reactionCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isActive;
}

