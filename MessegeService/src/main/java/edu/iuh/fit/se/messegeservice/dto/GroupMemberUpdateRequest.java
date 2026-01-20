package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupMemberUpdateRequest {
    private String requesterId;
    private List<String> participantIds;
}

