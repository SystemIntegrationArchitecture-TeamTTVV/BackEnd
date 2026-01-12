package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    private String id;
    
    private String name; // Tên sự kiện
    private String description; // Mô tả
    private String coverPhoto; // URL ảnh bìa
    
    @DBRef
    private User host; // Người tổ chức
    
    @DBRef
    private Group group; // null nếu không thuộc nhóm
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location; // Địa điểm
    private String address; // Địa chỉ chi tiết
    private String city;
    private String country;
    
    private String privacy = "PUBLIC"; // PUBLIC, PRIVATE, FRIENDS
    private int participantCount = 0;
    private int interestedCount = 0;
    
    private List<String> tags;
    private String category; // Danh mục sự kiện
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isActive = true;
    private boolean isCancelled = false;
}

