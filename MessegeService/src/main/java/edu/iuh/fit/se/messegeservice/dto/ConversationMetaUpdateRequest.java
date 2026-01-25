package edu.iuh.fit.se.messegeservice.dto;

import lombok.Data;

@Data
public class ConversationMetaUpdateRequest {
    private String requesterId;
    private String groupName;
    private String groupAvatar;
    private Boolean approvalsRequired;
}


