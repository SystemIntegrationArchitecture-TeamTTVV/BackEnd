package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    @Id
    private String id;
    
    @DBRef
    private User author;
    
    private String content; // Nội dung bài viết
    private List<String> images; // URLs ảnh
    private List<String> videos; // URLs video
    private String location; // Địa điểm
    private String feeling; // Cảm xúc
    private String activity; // Hoạt động
    
    private String visibility = "PUBLIC"; // PUBLIC, FRIENDS, ONLY_ME
    private boolean allowComments = true;
    private boolean allowSharing = true;
    
    // Thống kê
    private int likeCount = 0;
    private int commentCount = 0;
    private int shareCount = 0;
    
    // Reference đến Group hoặc Page nếu đăng trong nhóm/trang
    @DBRef
    private Group group; // null nếu không đăng trong nhóm
    @DBRef
    private Page page; // null nếu không đăng trong trang
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt; // Soft delete
    private boolean isDeleted = false;
}

