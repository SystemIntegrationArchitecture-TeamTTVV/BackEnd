package edu.iuh.fit.se.messegeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    @JsonProperty("approvalsRequired")
    private Boolean approvalsRequired;
    private List<String> pendingJoinIds;
    @JsonProperty("isGroup")
    private Boolean isGroup;
    private String groupName;
    private String groupAvatar;
    private String description;
    @JsonProperty("onlyAdminsCanSend")
    private Boolean onlyAdminsCanSend;
    @JsonProperty("onlyAdminsCanAddMembers")
    private Boolean onlyAdminsCanAddMembers;
    private Boolean hiddenForCurrentUser;
    private Boolean hiddenRequiresPin;
    private LocalDateTime clearBeforeAt;
    private String lastMessagePreview;
    private String lastMessageType;
    private String lastMessageSenderId;
    private String lastMessageSenderName;
    private LocalDateTime lastMessageAt;
    private Boolean isDisbanded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Phase A: Quick Wins ─────────────────────────────────────────────────
    private List<String> mutedByUserIds;
    private List<String> pinnedByUserIds;
    private List<String> bannedUserIds;
    private Map<String, String> nicknames;
    private String inviteLinkToken;
    private List<String> blockedByUserIds;
    private List<String> messageBlockedByUserIds;
    private List<String> callBlockedByUserIds;
    private String backgroundUrl;
    private Boolean aiAssistantEnabled;

    /**
     * Giữ lại kiểu getter isGroup() quen thuộc nhưng null-safe.
     * Nếu group == null thì coi như false.
     */
    @JsonIgnore
    public boolean isGroup() {
        return Boolean.TRUE.equals(isGroup);
    }

    public boolean isDisbanded() {
        return Boolean.TRUE.equals(isDisbanded);
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

    @JsonIgnore
    public boolean isOnlyAdminsCanSend() {
        return Boolean.TRUE.equals(onlyAdminsCanSend);
    }

    @JsonIgnore
    public boolean isOnlyAdminsCanAddMembers() {
        return onlyAdminsCanAddMembers == null || Boolean.TRUE.equals(onlyAdminsCanAddMembers);
    }
}

