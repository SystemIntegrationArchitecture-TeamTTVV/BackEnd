package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private String id;
    private String postId;
    private String userId;
    private String userName;
    private String userAvatar;
    private String content;
    private List<String> images;
    private String parentCommentId;
    private int likeCount;
    private int replyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

