package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConsultationSummaryDTO {
    private long total;
    private long pending;
    private long interested;
    private long registered;
    private long notInterested;
    private long callback;
}
