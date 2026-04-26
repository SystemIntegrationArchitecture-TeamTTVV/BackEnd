package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {
    @Id
    private String id;

    private String userId;

    private String postId;
    private String commentId;
    private String videoId;

    @DBRef
    private Post post;

    @DBRef
    private Comment comment;

    @DBRef
    private Video video;

    private String type;

    private LocalDateTime createdAt;
}
