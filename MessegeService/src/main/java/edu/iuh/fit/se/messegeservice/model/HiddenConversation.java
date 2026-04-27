package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

@Document(collection = "hidden_conversations")
@CompoundIndexes({
    @CompoundIndex(name = "user_conv_idx", def = "{'userId': 1, 'conversationId': 1}", unique = true),
    @CompoundIndex(name = "conv_hidden_idx", def = "{'conversationId': 1, 'hidden': 1, 'requirePinUnlock': 1}")
})
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
