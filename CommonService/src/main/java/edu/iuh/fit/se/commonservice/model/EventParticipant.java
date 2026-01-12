package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Document(collection = "event_participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_event_idx", def = "{'userId': 1, 'eventId': 1}", unique = true)
public class EventParticipant {
    @Id
    private String id;
    
    @DBRef
    private User user;
    private String userId;
    
    @DBRef
    private Event event;
    private String eventId;
    
    private String status = "GOING"; // GOING, INTERESTED, NOT_GOING
    
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
}

