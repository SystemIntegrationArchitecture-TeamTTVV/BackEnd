package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class LeaveGroupRequest {
    private String requesterId;
    /**
     * Optional. Required when requester is current owner.
     * Must be an existing participant (and not the requester).
     */
    private String newOwnerId;
}


