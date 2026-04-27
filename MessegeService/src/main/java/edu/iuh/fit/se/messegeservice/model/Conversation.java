package edu.iuh.fit.se.messegeservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "participants_lastMessageAt_idx", def = "{'participantIds': 1, 'lastMessageAt': -1}"),
    @CompoundIndex(name = "isGroup_lastMessageAt_idx", def = "{'isGroup': 1, 'lastMessageAt': -1}"),
    @CompoundIndex(name = "participants_isGroup_lastMessageAt_idx", def = "{'participantIds': 1, 'isGroup': 1, 'lastMessageAt': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    @Id
    private String id;

    private List<String> participantIds; // danh sách userId
    private List<String> participantNames; // để hiển thị nhanh
    private List<String> participantAvatars; // URL avatar

    // Group management
    private String ownerId;
    private List<String> adminIds;
    private boolean approvalsRequired = false;
    private List<String> pendingJoinIds;

    private boolean isGroup = false;
    private String groupName;
    private String groupAvatar;
    private String description;

    /**
     * When true, only owner/admin can send messages in this group.
     */
    private boolean onlyAdminsCanSend = false;

    /**
     * When true, only owner/admin can add new members.
     */
    /**
     * When true, only owner/admin can add new members.
     */
    private boolean onlyAdminsCanAddMembers = true;

    // ── Mute / Pin / Ban ────────────────────────────────────────────────────
    /** UserIds who muted this conversation (no notifications). */
    private List<String> mutedByUserIds;

    /** UserIds who pinned this conversation to top of their list. */
    private List<String> pinnedByUserIds;

    /** UserIds who are banned from this group (cannot rejoin). */
    private List<String> bannedUserIds;

    // ── Nicknames ────────────────────────────────────────────────────────────
    /** Per-user display nicknames within this conversation. Key = userId, Value = nickname. */
    private java.util.Map<String, String> nicknames;

    // ── Invite Link ─────────────────────────────────────────────────────────
    /** Unique invite token for group join-by-link. */
    @Indexed
    private String inviteLinkToken;

    /** UserIds who blocked the other party (only relevant for 1-1). */
    private List<String> blockedByUserIds;

    /** Custom background URL/image for the conversation. */
    private String backgroundUrl;

    /** Enable AI Assistant for this conversation. */
    private boolean aiAssistantEnabled = false;

    private String lastMessagePreview;
    private String lastMessageType;
    private String lastMessageSenderId;
    private String lastMessageSenderName;
    private LocalDateTime lastMessageAt;

    private boolean isDisbanded = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
