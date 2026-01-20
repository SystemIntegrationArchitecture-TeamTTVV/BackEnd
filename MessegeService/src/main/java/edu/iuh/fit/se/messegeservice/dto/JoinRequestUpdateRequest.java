package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class JoinRequestUpdateRequest {
    private String requesterId;
    private String approverId;
    private boolean approved;
}


