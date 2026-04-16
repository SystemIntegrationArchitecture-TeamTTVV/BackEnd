package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class TypingEventRequest {
    private String userId;
    private boolean typing;
}
