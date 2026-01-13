package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupDTO {
    private String id;
    private String name;
    private String description;
    private String coverPhoto;
    private String avatar;
    private String adminId;
    private String adminName;
    private String privacy;
    private String visibility;
    private int memberCount;
    private int postCount;
    private List<String> tags;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isActive;
}

