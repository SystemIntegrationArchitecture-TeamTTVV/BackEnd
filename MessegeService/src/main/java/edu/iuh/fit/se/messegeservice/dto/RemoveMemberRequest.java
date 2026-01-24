package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class RemoveMemberRequest {
    private String requesterId;
    private String participantId;
}

