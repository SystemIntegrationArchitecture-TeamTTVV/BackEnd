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
    private String generatedQuery; // Pipeline JSON đã sinh (tương đương generatedSql trong tham khảo)
    private Object data;           // Raw data từ MongoDB (tương đương response.get("data") trong tham khảo)
    private String mode;           // "CHAT" hoặc "DATA_QUERY"
}

