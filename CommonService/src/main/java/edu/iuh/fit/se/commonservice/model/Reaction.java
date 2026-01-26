package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_post_idx", def = "{'userId': 1, 'postId': 1}", unique = true, sparse = true)
@CompoundIndex(name = "user_comment_idx", def = "{'userId': 1, 'commentId': 1}", unique = true, sparse = true)
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
    
    private LocalDateTime createdAt;
}

