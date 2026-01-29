package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Video {
    @Id
    private String id;

    @DBRef
    private User author;

    private String title; // Tiêu đề video
    private String description; // Mô tả
    private String videoUrl; // URL video chính
    private String thumbnailUrl; // Ảnh thumbnail

    // Metadata
    private Long duration; // Thời lượng (giây)
    private String quality; // HD, FHD, 4K
    private Long fileSize; // Kích thước file

    private String visibility = "PUBLIC"; // PUBLIC, FRIENDS, ONLY_ME
    private Boolean allowComments = true;
    private Boolean allowReactions = true;

    // Thống kê
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer commentCount = 0;
    private Integer shareCount = 0;

    // Reference đến Group hoặc Page nếu đăng trong nhóm/trang
    @DBRef
    private Group group;
    @DBRef
    private Page page;

    // Tags và danh mục
    private List<String> tags;
    private String category; // Gaming, Music, Sports, etc.

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private boolean isDeleted = false;
}