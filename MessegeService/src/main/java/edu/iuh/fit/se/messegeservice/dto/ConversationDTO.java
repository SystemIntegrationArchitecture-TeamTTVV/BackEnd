package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private String id;
    private List<String> participantIds;
    private List<String> participantNames;
    private List<String> participantAvatars;
    private boolean group;
    private String groupName;
    private String groupAvatar;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

