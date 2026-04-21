package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class DeliveredEventRequest {
    private String userId;
    private String lastDeliveredMessageId;
}
