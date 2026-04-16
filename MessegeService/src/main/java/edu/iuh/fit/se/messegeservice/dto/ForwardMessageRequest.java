package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class ForwardMessageRequest {
    private String requesterId;
    private String targetConversationId;
    private String note;
}
