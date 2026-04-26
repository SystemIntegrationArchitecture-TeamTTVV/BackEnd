package edu.iuh.fit.se.commonservice.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Integer viewCount;
    private Integer reactionCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean active;
}

