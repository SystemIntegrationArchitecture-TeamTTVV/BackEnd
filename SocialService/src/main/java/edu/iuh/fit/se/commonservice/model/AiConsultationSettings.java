package edu.iuh.fit.se.commonservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "ai_consultation_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConsultationSettings {
    @Id
    private String id; // usually a single document "singleton"
    
    private String packages;
    private String instructions;
    private String wsUrl;
    private String groqApiKey;
    
    // Twilio config
    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioPhoneNumber;
}
