package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupInviteDTO {
    private String id;
    private String conversationId;
    private String inviterId;
    private String inviteeId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Optional enriched data
    private ConversationDTO conversation;
}
