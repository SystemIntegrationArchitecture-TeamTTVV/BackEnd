package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private String id;

    @DBRef
    private Conversation conversation;
    private String conversationId;

    private String senderId;
    private String senderName;
    private String senderAvatar;

    private String content; // text
    private List<String> emojis; // quick reactions on message

    private List<MessageAttachment> attachments;

    private boolean isDeleted = false;
    private boolean isEdited = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

