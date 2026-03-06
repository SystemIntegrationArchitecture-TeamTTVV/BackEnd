package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    private String type; // LIKE, LOVE, HAHA, WOW, SAD, ANGRY
    
    // Có thể reaction cho Post hoặc Comment
    @DBRef
    private Post post;
    private String postId;
    
    @DBRef
    private Comment comment;
    private String commentId;

    @DBRef
    private Video video;
    private String videoId;
    
    private LocalDateTime createdAt;
}

