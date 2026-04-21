package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationActionRequest {
    private String userId;
    // Optional additional payload for nickname or token
    private String payload;
}
