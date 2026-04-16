package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class SeenEventRequest {
    private String userId;
    private String lastSeenMessageId;
}
