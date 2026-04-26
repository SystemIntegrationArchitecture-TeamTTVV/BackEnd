package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostDTO {
    private String id;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private List<String> images;
    private List<String> videos;
    private String location;
    private String feeling;
    private String activity;
    private String visibility;
    private Boolean allowComments;
    private Boolean allowSharing;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private String groupId;
    private String pageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

