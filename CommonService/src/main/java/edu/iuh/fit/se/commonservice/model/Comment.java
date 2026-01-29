package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    private String id;
    
    @DBRef
    private Post post;
    
    @DBRef
    private User author;

    @DBRef
    private Video video;
    
    private String content;
    private List<String> images; // URLs ảnh trong comment
    
    @DBRef
    private Comment parentComment; // null nếu là comment gốc, có giá trị nếu là reply
    
    private int likeCount = 0;
    private int replyCount = 0;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private boolean isDeleted = false;
}

