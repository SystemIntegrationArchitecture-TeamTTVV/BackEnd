package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class GroupRoleUpdateRequest {
    private String requesterId;
    private String newOwnerId;
    private List<String> adminIds;
}

