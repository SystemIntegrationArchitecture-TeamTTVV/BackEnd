package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;

@Document(collection = "photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photo {
    @Id
    private String id;
    
    private String url; // URL ảnh
    private String thumbnailUrl; // URL thumbnail
    private String caption; // Chú thích ảnh
    
    @DBRef
    private User owner;
    
    @DBRef
    private Album album; // null nếu không thuộc album
    
    @DBRef
    private Post post; // null nếu không thuộc post
    
    private int width;
    private int height;
    private long fileSize; // bytes
    
    private int likeCount = 0;
    private int commentCount = 0;
    
    private String location; // Địa điểm chụp ảnh
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

