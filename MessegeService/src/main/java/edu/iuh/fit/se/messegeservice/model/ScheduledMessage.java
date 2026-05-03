package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "scheduled_messages")
@CompoundIndexes({
    @CompoundIndex(name = "conv_sender_idx", def = "{'conversationId': 1, 'senderId': 1}"),
    @CompoundIndex(name = "status_scheduledAt_idx", def = "{'status': 1, 'scheduledAt': 1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledMessage {
    @Id
    private String id;

    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;

    private String content;
    private List<MessageAttachment> attachments;

    private LocalDateTime scheduledAt;

    /** PENDING | SENT | CANCELLED */
    private String status = "PENDING";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
