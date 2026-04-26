package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    @Id
    private String id;

    private String authorId;

    private String title;
    private String description;
    private String videoUrl;
    private String thumbnailUrl;
    private Long duration;
    private String quality;
    private Long fileSize;

    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer commentCount = 0;
    private Integer shareCount = 0;

    private String visibility = "PUBLIC";
    private Boolean allowComments = true;
    private Boolean allowReactions = true;
    private List<String> tags;
    private String category;

    @DBRef
    private Group group;
    private String groupId;

    @DBRef
    private Page page;
    private String pageId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private boolean isDeleted = false;
}
