package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class CallActionRequest {
    private String userId;
    private String transferToUserId; // For host transfer when leaving
}
