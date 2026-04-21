package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDTO {
    private String id;
    private String conversationId;
    private String creatorId;
    private String content;
    private LocalDateTime remindAt;
    private boolean isTriggered;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
