package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIChatResponseDTO {
    private String response;
    private String conversationId; // Để frontend có thể tiếp tục conversation
}

