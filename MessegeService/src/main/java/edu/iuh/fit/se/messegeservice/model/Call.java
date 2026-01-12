package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Call {
    @Id
    private String id;

    private String conversationId;
    private String callerId;
    private List<String> calleeIds;

    private String type; // VOICE, VIDEO
    private String status; // MISSED, COMPLETED, DECLINED

    private int durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}

