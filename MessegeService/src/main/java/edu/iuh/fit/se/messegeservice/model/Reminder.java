package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reminders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reminder {
    @Id
    private String id;
    
    private String conversationId;
    private String creatorId;
    private String content;
    private LocalDateTime remindAt;
    
    private boolean isTriggered = false;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
