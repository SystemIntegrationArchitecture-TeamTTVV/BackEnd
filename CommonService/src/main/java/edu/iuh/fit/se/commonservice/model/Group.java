package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Id
    private String id;
    
    private String name; // Tên nhóm
    private String description; // Mô tả nhóm
    private String coverPhoto; // URL ảnh bìa
    private String avatar; // URL ảnh đại diện
    
    @DBRef
    private User admin; // Người quản trị nhóm
    
    private String privacy = "PUBLIC"; // PUBLIC, PRIVATE, SECRET
    private String visibility = "VISIBLE"; // VISIBLE, HIDDEN
    
    private int memberCount = 0;
    private int postCount = 0;
    
    private List<String> tags; // Thẻ tag
    private String category; // Danh mục nhóm
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isActive = true;
}

