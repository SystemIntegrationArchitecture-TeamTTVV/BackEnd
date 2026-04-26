package edu.iuh.fit.se.commonservice.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoDTO {
    private String id;
    private String authorId;
    private String authorName;
    private String authorAvatar;

    private String title;
    private String description;
    private String videoUrl;
    private String thumbnailUrl;

    private Long duration;
    private String quality;
    private Long fileSize;

    private String visibility;
    private Boolean allowComments;
    private Boolean allowReactions;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;

    private String groupId;
    private String pageId;

    private List<String> tags;
    private String category;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}