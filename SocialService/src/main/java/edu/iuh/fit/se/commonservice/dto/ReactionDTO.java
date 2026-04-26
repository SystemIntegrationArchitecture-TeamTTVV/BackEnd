package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionDTO {
    private String id;
    private String userId;
    private String userName;
    private String userAvatar;
    private String type; // LIKE, LOVE, HAHA, WOW, SAD, ANGRY
    private String postId;
    private String commentId;
    private String videoId;
    private LocalDateTime createdAt;
}

