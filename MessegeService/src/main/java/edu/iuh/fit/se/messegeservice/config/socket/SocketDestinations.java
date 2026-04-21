package edu.iuh.fit.se.messegeservice.config.socket;

/**
 * Mirror of CommonService's SocketDestinations.
 */
public final class SocketDestinations {

    private SocketDestinations() {
        // utility class
    }

    public static final String BROKER_TOPIC = "/topic";
    public static final String BROKER_QUEUE = "/queue";
    public static final String BROKER_USER = "/user";
    public static final String APP_PREFIX = "/app";
    public static final String USER_PREFIX = "/user";

    public static final String TOPIC_PUBLIC = "/topic/public";
    public static final String TOPIC_ROOMS_PREFIX = "/topic/rooms.";
    public static final String QUEUE_NOTIFICATIONS = "/queue/notifications";
    public static final String QUEUE_WEBRTC = "/queue/webrtc";

    public static String roomDestination(String conversationId) {
        return TOPIC_ROOMS_PREFIX + conversationId;
    }
}
