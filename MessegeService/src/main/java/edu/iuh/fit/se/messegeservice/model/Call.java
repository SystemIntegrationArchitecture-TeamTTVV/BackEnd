package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private String status; // legacy — kept for backward compat

    // ── New fields for smart call lifecycle ──
    private String callType;  // DIRECT | GROUP
    private String hostId;    // userId who created the call
    private List<String> activeParticipantIds = new ArrayList<>();
    private List<String> leftParticipantIds = new ArrayList<>();
    private String state; // RINGING | CONNECTED | ENDED

    private int durationSeconds;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /** Max participants for group calls */
    public static final int MAX_GROUP_PARTICIPANTS = 5;
}
