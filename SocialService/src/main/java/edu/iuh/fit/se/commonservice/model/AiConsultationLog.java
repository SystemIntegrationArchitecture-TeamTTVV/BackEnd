package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "ai_consultation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConsultationLog {
    @Id
    private String id;
    private String callId;
    private String userId;
    private String userName;
    private String userAvatar;
    private String phoneNumber;
    
    // calling | completed | failed | no-answer
    private String status;
    
    // pending | interested | registered | not_interested | callback
    private String result;
    
    private String recommendedPackage;
    private Integer duration = 0;
    private String notes;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
