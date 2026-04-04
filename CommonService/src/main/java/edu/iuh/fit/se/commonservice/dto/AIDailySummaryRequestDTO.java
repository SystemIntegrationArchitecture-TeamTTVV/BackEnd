package edu.iuh.fit.se.commonservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIDailySummaryRequestDTO {
    private String userId;
    private Integer limit;
}
