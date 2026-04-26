package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendDTO {
    private String id;
    private String userId;
    private String userName;
    private String userAvatar;
    private String friendId;
    private String friendName;
    private String friendAvatar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

