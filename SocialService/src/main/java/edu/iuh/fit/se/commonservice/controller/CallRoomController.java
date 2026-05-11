package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Room-based WebRTC signaling controller.
 * Emulates Socket.IO room behavior using STOMP topics.
 *
 * Flow (giống bài tham khảo):
 * 1. Client gửi "join" → server broadcast "room-users" (danh sách users hiện tại)
 * 2. Client nhận "room-users" → tạo offer đến mỗi user
 * 3. Client gửi "signal" → server relay đến target user qua personal queue
 * 4. Client gửi "leave" hoặc disconnect → server broadcast "user-left"
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CallRoomController {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthServiceClient authServiceClient;

    // Room membership: roomId → Set<UserInfo>
    private final ConcurrentHashMap<String, Set<RoomUser>> rooms = new ConcurrentHashMap<>();
    // Session tracking: sessionId → {roomId, username, userId}
    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    // Username cache
    private final ConcurrentHashMap<String, String> userIdToUsername = new ConcurrentHashMap<>();

    @Data
    static class RoomUser {
        private String odinalId; // backend socket-level unique ID (= username for STOMP)
        private String userId;
        private String name;
        private String avatar;
    }

    @Data
    static class SessionInfo {
        private String roomId;
        private String username;
        private String odinalId;
        private String userId;
    }

    /**
     * Client joins a call room.
     * Destination: /app/call-room/{roomId}/join
     * Payload: { userId, name, avatar }
     */
    @MessageMapping("/call-room/{roomId}/join")
    public void handleJoin(
            @DestinationVariable String roomId,
            Map<String, Object> payload,
            StompHeaderAccessor accessor
    ) {
        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        String sessionId = accessor.getSessionId();

        if (username == null || username.isBlank()) {
            log.warn("❌ Join failed: no principal");
            return;
        }

        String userId = payload.get("userId") != null ? payload.get("userId").toString() : username;
        String name = payload.get("name") != null ? payload.get("name").toString() : username;
        String avatar = payload.get("avatar") != null ? payload.get("avatar").toString() : "";

        // Create RoomUser
        RoomUser user = new RoomUser();
        user.setOdinalId(username); // STOMP uses username as principal
        user.setUserId(userId);
        user.setName(name);
        user.setAvatar(avatar);

        // Cache username
        userIdToUsername.put(userId, username);

        // Get existing users before adding
        Set<RoomUser> roomUsers = rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());

        // Remove any existing entry for same user (re-join)
        roomUsers.removeIf(u -> u.getUserId().equals(userId));
        roomUsers.add(user);

        // Track session for disconnect cleanup
        SessionInfo si = new SessionInfo();
        si.setRoomId(roomId);
        si.setUsername(username);
        si.setOdinalId(username);
        si.setUserId(userId);
        sessions.put(sessionId, si);

        log.info("📞 User {} joined room {} ({} users)", userId, roomId, roomUsers.size());

        // Send room-users list to the joining user (via personal queue)
        List<Map<String, String>> userList = new ArrayList<>();
        for (RoomUser ru : roomUsers) {
            Map<String, String> m = new HashMap<>();
            m.put("odinalId", ru.getOdinalId());
            m.put("userId", ru.getUserId());
            m.put("name", ru.getName());
            m.put("avatar", ru.getAvatar());
            userList.add(m);
        }
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/call-room",
                Map.of("type", "room-users", "roomId", roomId, "users", userList)
        );

        // Broadcast "user-joined" to all OTHER users in the room
        Map<String, Object> joinEvent = new HashMap<>();
        joinEvent.put("type", "user-joined");
        joinEvent.put("roomId", roomId);
        joinEvent.put("odinalId", username);
        joinEvent.put("userId", userId);
        joinEvent.put("name", name);
        joinEvent.put("avatar", avatar);

        for (RoomUser ru : roomUsers) {
            if (!ru.getUserId().equals(userId)) {
                messagingTemplate.convertAndSendToUser(
                        ru.getOdinalId(),
                        "/queue/call-room",
                        joinEvent
                );
            }
        }
    }

    /**
     * Client sends a WebRTC signal (offer/answer/ICE candidate) to a specific peer.
     * Destination: /app/call-room/{roomId}/signal
     * Payload: { to: targetUserId, type: "offer"|"answer"|"candidate", data: {...} }
     */
    @MessageMapping("/call-room/{roomId}/signal")
    public void handleSignal(
            @DestinationVariable String roomId,
            Map<String, Object> payload,
            StompHeaderAccessor accessor
    ) {
        Principal principal = accessor.getUser();
        String fromUsername = principal != null ? principal.getName() : null;
        if (fromUsername == null) return;

        String targetUserId = payload.get("to") != null ? payload.get("to").toString() : null;
        String signalType = payload.get("type") != null ? payload.get("type").toString() : null;
        Object signalData = payload.get("data");

        if (targetUserId == null || signalType == null) {
            log.warn("❌ Signal missing 'to' or 'type'");
            return;
        }

        // Find target username
        String targetUsername = resolveUsername(targetUserId);
        if (targetUsername == null) {
            log.warn("❌ Cannot resolve username for userId: {}", targetUserId);
            return;
        }

        // Find sender's userId from session
        String senderUserId = null;
        for (SessionInfo si : sessions.values()) {
            if (fromUsername.equals(si.getUsername())) {
                senderUserId = si.getUserId();
                break;
            }
        }

        // Relay signal to target user
        Map<String, Object> signalEvent = new HashMap<>();
        signalEvent.put("type", "signal");
        signalEvent.put("from", senderUserId != null ? senderUserId : fromUsername);
        signalEvent.put("fromOdinalId", fromUsername);
        signalEvent.put("signalType", signalType);
        signalEvent.put("data", signalData);
        signalEvent.put("roomId", roomId);

        messagingTemplate.convertAndSendToUser(
                targetUsername,
                "/queue/call-room",
                signalEvent
        );

        log.debug("📡 Signal {} from {} to {} in room {}", signalType, fromUsername, targetUsername, roomId);
    }

    /**
     * Client explicitly leaves a call room.
     * Destination: /app/call-room/{roomId}/leave
     */
    @MessageMapping("/call-room/{roomId}/leave")
    public void handleLeave(
            @DestinationVariable String roomId,
            StompHeaderAccessor accessor
    ) {
        Principal principal = accessor.getUser();
        String username = principal != null ? principal.getName() : null;
        String sessionId = accessor.getSessionId();

        if (username == null) return;

        removeUserFromRoom(roomId, username, sessionId);
    }

    /**
     * Auto-cleanup when WebSocket disconnects.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        SessionInfo si = sessions.remove(sessionId);
        if (si != null) {
            log.info("🔌 Session {} disconnected, removing from room {}", sessionId, si.getRoomId());
            removeUserFromRoom(si.getRoomId(), si.getUsername(), null);
        }
    }

    private void removeUserFromRoom(String roomId, String username, String sessionId) {
        Set<RoomUser> roomUsers = rooms.get(roomId);
        if (roomUsers == null) return;

        RoomUser removed = null;
        for (RoomUser u : roomUsers) {
            if (u.getOdinalId().equals(username)) {
                removed = u;
                break;
            }
        }

        if (removed != null) {
            roomUsers.remove(removed);
            log.info("❌ User {} left room {} ({} remaining)", username, roomId, roomUsers.size());

            // Broadcast "user-left" to remaining users
            Map<String, Object> leftEvent = new HashMap<>();
            leftEvent.put("type", "user-left");
            leftEvent.put("roomId", roomId);
            leftEvent.put("odinalId", username);
            leftEvent.put("userId", removed.getUserId());
            leftEvent.put("reason", "left");

            for (RoomUser ru : roomUsers) {
                messagingTemplate.convertAndSendToUser(
                        ru.getOdinalId(),
                        "/queue/call-room",
                        leftEvent
                );
            }

            // Clean up empty rooms
            if (roomUsers.isEmpty()) {
                rooms.remove(roomId);
            }
        }

        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private String resolveUsername(String userId) {
        // Check cache first
        String cached = userIdToUsername.get(userId);
        if (cached != null) return cached;

        // Check room users
        for (Set<RoomUser> roomUsers : rooms.values()) {
            for (RoomUser ru : roomUsers) {
                if (ru.getUserId().equals(userId)) {
                    userIdToUsername.put(userId, ru.getOdinalId());
                    return ru.getOdinalId();
                }
            }
        }

        // Fallback: query auth service
        try {
            UserDTO user = authServiceClient.getUserById(userId);
            if (user != null && user.getUsername() != null) {
                userIdToUsername.put(userId, user.getUsername());
                return user.getUsername();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve username for {}: {}", userId, e.getMessage());
        }

        return null;
    }
}
