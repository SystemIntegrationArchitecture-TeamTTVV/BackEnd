package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class ConversationPinRequest {
    private String userId;
    private String pin;
}
