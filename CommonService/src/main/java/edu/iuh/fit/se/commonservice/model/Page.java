package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Page {
    @Id
    private String id;
    
    private String name; // Tên trang
    private String username; // @username
    private String description; // Mô tả trang
    private String coverPhoto; // URL ảnh bìa
    private String avatar; // URL ảnh đại diện
    
    @DBRef
    private User admin; // Người quản trị trang
    
    private String category; // Danh mục trang
    private String website; // Website
    private String phoneNumber;
    private String email;
    private String address;
    private String city;
    private String country;
    
    private int likeCount = 0;
    private int followerCount = 0;
    private int postCount = 0;
    
    private List<String> tags;
    private boolean isVerified = false;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isActive = true;
}

