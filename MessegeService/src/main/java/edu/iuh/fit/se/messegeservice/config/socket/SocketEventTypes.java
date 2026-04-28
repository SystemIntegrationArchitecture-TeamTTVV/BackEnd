package edu.iuh.fit.se.messegeservice.config.socket;

/**
 * Mirror of CommonService's SocketEventTypes.
 * <p>
 * Khi thêm event mới: thêm ở CommonService trước, rồi mirror sang đây
 * và Frontend (socketEvents.ts).
 * </p>
 */
public final class SocketEventTypes {

    private SocketEventTypes() {
        // utility class
    }

    // ── Message Events ──────────────────────────────────────────────────────
    public static final String MESSAGE_RECEIVED = "MESSAGE_RECEIVED";
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String MESSAGE_DELETED_FOR_ME = "MESSAGE_DELETED_FOR_ME";
    public static final String MESSAGE_SEEN = "MESSAGE_SEEN";
    public static final String MESSAGE_DELIVERED = "MESSAGE_DELIVERED";
    public static final String TYPING = "TYPING";
    public static final String MESSAGE_EDITED = "MESSAGE_EDITED";
    public static final String MESSAGE_PINNED = "MESSAGE_PINNED";
    public static final String MESSAGE_REACTED = "MESSAGE_REACTED";
    public static final String MESSAGE_BLOCKED = "MESSAGE_BLOCKED";

    // ── Conversation Events ─────────────────────────────────────────────────
    public static final String CONVERSATION_CLEARED = "CONVERSATION_CLEARED";
    public static final String CONVERSATION_RESTORED = "CONVERSATION_RESTORED";
    public static final String CONVERSATION_META_UPDATED = "CONVERSATION_META_UPDATED";

    // ── Group Management Events ─────────────────────────────────────────────
    public static final String GROUP_RENAMED = "GROUP_RENAMED";
    public static final String MEMBERS_ADDED = "MEMBERS_ADDED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String OWNER_TRANSFERRED = "OWNER_TRANSFERRED";
    public static final String ADMINS_UPDATED = "ADMINS_UPDATED";
    public static final String JOIN_REQUEST_CREATED = "JOIN_REQUEST_CREATED";
    public static final String JOIN_REQUEST_UPDATED = "JOIN_REQUEST_UPDATED";
    public static final String JOIN_REQUEST_APPROVED = "JOIN_REQUEST_APPROVED";
    public static final String JOIN_APPROVALS_UPDATED = "JOIN_APPROVALS_UPDATED";
    public static final String SEND_PERMISSION_UPDATED = "SEND_PERMISSION_UPDATED";
    public static final String ADD_MEMBER_PERMISSION_UPDATED = "ADD_MEMBER_PERMISSION_UPDATED";

    // ── Poll Events ─────────────────────────────────────────────────────────
    public static final String POLL_CREATED = "POLL_CREATED";
    public static final String POLL_UPDATED = "POLL_UPDATED";

    // ── Appointment Events ──────────────────────────────────────────────────
    public static final String APPOINTMENT_CREATED = "APPOINTMENT_CREATED";
    public static final String APPOINTMENT_UPDATED = "APPOINTMENT_UPDATED";

    // ── Reminder Events ─────────────────────────────────────────────────────
    public static final String REMINDER_TRIGGERED = "REMINDER_TRIGGERED";

    // ── Presence Events ─────────────────────────────────────────────────────
    public static final String USER_PRESENCE_CHANGED = "USER_PRESENCE_CHANGED";

    // ── Social / Notification Events ────────────────────────────────────────
    public static final String NOTIFICATION = "NOTIFICATION";
    public static final String POST_CREATED = "POST_CREATED";
    public static final String POST_UPDATED = "POST_UPDATED";
    public static final String COMMENT_CREATED = "COMMENT_CREATED";
    public static final String REACTION_ADDED = "REACTION_ADDED";

    // ── WebRTC Call Events ──────────────────────────────────────────────────
    public static final String CALL_OFFER = "CALL_OFFER";
    public static final String CALL_ANSWER = "CALL_ANSWER";
    public static final String CALL_ICE_CANDIDATE = "CALL_ICE_CANDIDATE";
    public static final String CALL_REJECT = "CALL_REJECT";
    public static final String CALL_END = "CALL_END";
    public static final String CALL_USER_JOINED = "CALL_USER_JOINED";
    public static final String CALL_USER_LEFT = "CALL_USER_LEFT";
    public static final String CALL_HOST_TRANSFERRED = "CALL_HOST_TRANSFERRED";

    // ── LiveStream Events ──────────────────────────────────────────────────
    public static final String LIVE_STARTED = "LIVE_STARTED";
    public static final String LIVE_ENDED = "LIVE_ENDED";
    public static final String LIVE_VIEWER_COUNT = "LIVE_VIEWER_COUNT";
}
