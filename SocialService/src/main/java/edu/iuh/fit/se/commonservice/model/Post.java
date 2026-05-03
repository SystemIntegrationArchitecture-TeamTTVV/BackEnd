package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    @Id
    private String id;

    /** Author user id (canonical). Legacy documents may only have DBRef "author" in Mongo. */
    private String authorId;

    private String content;
    private List<String> images;
    private List<String> videos;
    private String location;
    private String feeling;
    private String activity;

    private String visibility = "PUBLIC";
    private Boolean allowComments = true;
    private Boolean allowSharing = true;

    private Integer likeCount = 0;
    private Integer commentCount = 0;
    private Integer shareCount = 0;

    @DBRef
    private Group group;
    private String groupId;
    @DBRef
    private Page page;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private boolean isDeleted = false;
    private boolean isPinned = false;
}
