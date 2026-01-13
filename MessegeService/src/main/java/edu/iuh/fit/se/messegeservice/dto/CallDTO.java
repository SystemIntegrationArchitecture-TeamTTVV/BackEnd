package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallDTO {
    private String id;
    private String conversationId;
    private String callerId;
    private List<String> calleeIds;
    private String type; // VOICE, VIDEO
    private String status; // COMPLETED, MISSED, REJECTED, ONGOING
    private int durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}

