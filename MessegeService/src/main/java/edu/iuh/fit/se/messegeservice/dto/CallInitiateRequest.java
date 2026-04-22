package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class CallInitiateRequest {
    private String conversationId;
    private String callerId;
    private List<String> calleeIds;
    private String type; // VOICE, VIDEO
}
