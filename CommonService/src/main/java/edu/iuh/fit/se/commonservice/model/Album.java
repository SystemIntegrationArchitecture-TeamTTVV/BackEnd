package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "albums")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Album {
    @Id
    private String id;
    
    private String name; // Tên album
    private String description; // Mô tả album
    
    @DBRef
    private User owner;
    
    private String coverPhoto; // URL ảnh bìa album
    private int photoCount = 0;
    
    private String privacy = "PUBLIC"; // PUBLIC, FRIENDS, ONLY_ME
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

