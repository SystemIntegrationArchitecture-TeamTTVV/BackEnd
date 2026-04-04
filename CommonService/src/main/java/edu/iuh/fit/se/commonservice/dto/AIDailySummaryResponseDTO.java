package edu.iuh.fit.se.commonservice.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIDailySummaryResponseDTO {
    private String summary;
    private int notificationsCount;
    private int friendsPostCount;
    private int incomingMessageCount;
    private LocalDateTime generatedAt;
}
