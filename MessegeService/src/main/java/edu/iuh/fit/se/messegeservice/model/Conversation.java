package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    private String id;

    private List<String> participantIds; // danh sách userId
    private List<String> participantNames; // để hiển thị nhanh
    private List<String> participantAvatars; // URL avatar

    // Group management
    private String ownerId;
    private List<String> adminIds;

    private boolean isGroup = false;
    private String groupName;
    private String groupAvatar;

    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

