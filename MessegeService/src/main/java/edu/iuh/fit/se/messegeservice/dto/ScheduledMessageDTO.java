package edu.iuh.fit.se.messegeservice.dto;

import edu.iuh.fit.se.messegeservice.model.MessageAttachment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledMessageDTO {
    private String id;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private List<MessageAttachment> attachments;
    private LocalDateTime scheduledAt;
    /** PENDING | SENT | CANCELLED */
    private String status;
    private LocalDateTime createdAt;
}
