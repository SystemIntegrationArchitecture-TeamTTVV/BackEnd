package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LiveChatRequest {
    private String userId;
    private String userName;
    private String content;
}
