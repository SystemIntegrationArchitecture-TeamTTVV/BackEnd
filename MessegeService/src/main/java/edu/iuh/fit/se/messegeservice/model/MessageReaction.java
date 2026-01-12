package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "message_reactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_message_idx", def = "{'userId':1,'messageId':1}", unique = true)
public class MessageReaction {
    @Id
    private String id;

    private String messageId;
    private String conversationId;

    private String userId;
    private String emoji; // 👍 ❤️ 😂 😮 😢 😡

    private LocalDateTime createdAt;
}

