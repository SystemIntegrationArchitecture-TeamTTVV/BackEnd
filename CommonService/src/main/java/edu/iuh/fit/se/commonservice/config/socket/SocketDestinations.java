package edu.iuh.fit.se.commonservice.config.socket;

/**
 * Single source-of-truth cho tất cả STOMP destination paths.
 * <p>
 * Khi thêm channel mới: thêm constant ở đây, rồi mirror sang MessegeService
 * và Frontend (socketEvents.ts).
 * </p>
 */
public final class SocketDestinations {

    private SocketDestinations() {
        // utility class
    }

    // ── Broker prefixes (used in WebSocketConfig) ───────────────────────────
    public static final String BROKER_TOPIC = "/topic";
    public static final String BROKER_QUEUE = "/queue";
    public static final String BROKER_USER = "/user";
    public static final String APP_PREFIX = "/app";
    public static final String USER_PREFIX = "/user";

    // ── Public broadcast ────────────────────────────────────────────────────
    public static final String TOPIC_PUBLIC = "/topic/public";

    // ── Conversation room prefix ────────────────────────────────────────────
    public static final String TOPIC_ROOMS_PREFIX = "/topic/rooms.";

    // ── User-specific queues ────────────────────────────────────────────────
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String QUEUE_WEBRTC = "/queue/webrtc";

    /**
     * Build full room destination: /topic/rooms.{conversationId}
     */
    public static String roomDestination(String conversationId) {
        return TOPIC_ROOMS_PREFIX + conversationId;
    }
}
