package edu.iuh.fit.se.commonservice.dto;

import lombok.Data;

@Data
public class UpdateLogResultDTO {
    private String result;
    private String notes;
    private String recommendedPackage;
    private Integer duration;
}
