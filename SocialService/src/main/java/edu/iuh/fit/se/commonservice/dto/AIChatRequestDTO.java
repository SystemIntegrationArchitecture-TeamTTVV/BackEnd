package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIChatRequestDTO {
    private String message;
    private String userId; // Optional: để lưu lịch sử chat theo user
    private String conversationId; // Optional: để tiếp tục conversation
    private String mode; // "CHAT" (default) hoặc "DATA_QUERY" — tương đương StudentChatRequest.mode
}

