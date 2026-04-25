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

    private String authorId;

    private String type;
    private String mediaUrl;
    private String thumbnailUrl;
    private String text;
    private String backgroundColor;
    private String visibility;

    private Integer viewCount;
    private Integer reactionCount;

    private Boolean active = true;
    private LocalDateTime expiresAt;

    @DBRef
    private Group group;

    private LocalDateTime createdAt;
}
