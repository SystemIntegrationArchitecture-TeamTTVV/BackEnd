package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "hidden_conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HiddenConversation {
    @Id
    private String id;

    private String userId;
    private String conversationId;
    private boolean hidden = true;
    private String pinHash;
    private boolean requirePinUnlock = false;

    /**
     * Messages created at or before this timestamp are hidden for this user.
     */
    private LocalDateTime clearBeforeAt;

    private LocalDateTime lastAccessAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
