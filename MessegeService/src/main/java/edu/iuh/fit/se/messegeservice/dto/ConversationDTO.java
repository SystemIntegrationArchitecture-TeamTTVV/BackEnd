package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private String id;
    private List<String> participantIds;
    private List<String> participantNames;
    private List<String> participantAvatars;
    private String ownerId;
    private List<String> adminIds;
    // Dùng wrapper Boolean để tránh lỗi khi client gửi null cho field boolean
    private Boolean approvalsRequired;
    private List<String> pendingJoinIds;
    @JsonProperty("isGroup")
    private Boolean isGroup;
    private String groupName;
    private String groupAvatar;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Giữ lại kiểu getter isGroup() quen thuộc nhưng null-safe.
     * Nếu group == null thì coi như false.
     */
    @JsonIgnore
    public boolean isGroup() {
        return Boolean.TRUE.equals(isGroup);
    }

    // Giữ tương thích với code đang gọi setGroup(...)
    public void setGroup(Boolean group) {
        this.isGroup = group;
    }

    /**
     * Getter null-safe cho approvalsRequired.
     * Nếu approvalsRequired == null thì coi như false.
     */
    @JsonIgnore
    public boolean isApprovalsRequired() {
        return Boolean.TRUE.equals(approvalsRequired);
    }
}

