package edu.iuh.fit.se.commonservice.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private String id;
    private String postId;
    private String userId;
    private String videoId;
    private String userName;
    private String userAvatar;
    private String content;
    private List<String> images;
    private String parentCommentId;
    private Integer likeCount;
    private Integer replyCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

