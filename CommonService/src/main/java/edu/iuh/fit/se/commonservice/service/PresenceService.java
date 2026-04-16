package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.PresenceStatusDTO;
import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;
    private final SocketService socketService;

    private final ConcurrentHashMap<String, PresenceEntry> presenceByUsername = new ConcurrentHashMap<>();

    public void markOnline(String userId, String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        PresenceEntry entry = presenceByUsername.compute(username, (key, existing) -> {
            PresenceEntry current = existing == null ? new PresenceEntry() : existing;
            current.userId = userId;
            current.username = username;
            current.connectionCount = Math.max(0, current.connectionCount) + 1;
            current.lastSeenAt = LocalDateTime.now();
            return current;
        });

        if (entry != null && entry.connectionCount == 1) {
            publishPresenceChanged(entry.userId, entry.username, true, entry.lastSeenAt);
        }
    }

    public void heartbeat(String userId, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        presenceByUsername.compute(username, (key, existing) -> {
            PresenceEntry current = existing == null ? new PresenceEntry() : existing;
            current.userId = userId;
            current.username = username;
            current.connectionCount = Math.max(1, current.connectionCount);
            current.lastSeenAt = LocalDateTime.now();
            return current;
        });
    }

    public void markOffline(String userId, String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        PresenceEntry entry = presenceByUsername.computeIfPresent(username, (key, existing) -> {
            existing.userId = userId != null ? userId : existing.userId;
            existing.connectionCount = Math.max(0, existing.connectionCount - 1);
            existing.lastSeenAt = LocalDateTime.now();
            if (existing.connectionCount <= 0) {
                return null;
            }
            return existing;
        });

        if (entry == null) {
            LocalDateTime lastSeenAt = LocalDateTime.now();
            publishPresenceChanged(userId, username, false, lastSeenAt);
        }
    }

    public Map<String, PresenceStatusDTO> getPresenceByUserIds(List<String> userIds) {
        Map<String, PresenceStatusDTO> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        List<User> users = userRepository.findAllById(userIds);
        Map<String, User> byId = new LinkedHashMap<>();
        for (User user : users) {
            byId.put(user.getId(), user);
        }

        LocalDateTime now = LocalDateTime.now();
        for (String userId : userIds) {
            User user = byId.get(userId);
            String username = user != null ? user.getUsername() : null;
            PresenceEntry entry = username == null ? null : presenceByUsername.get(username);
            boolean online = entry != null && entry.connectionCount > 0;
            LocalDateTime lastSeenAt = online
                    ? (entry.lastSeenAt != null ? entry.lastSeenAt : now)
                    : (entry != null && entry.lastSeenAt != null ? entry.lastSeenAt : null);

            result.put(userId, new PresenceStatusDTO(userId, username, online, lastSeenAt));
        }

        return result;
    }

    public void markOnlineByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        markOnline(user != null ? user.getId() : null, username);
    }

    public void markOfflineByUsername(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        markOffline(user != null ? user.getId() : null, username);
    }

    public List<String> getOnlineUsernamesSnapshot() {
        return new ArrayList<>(presenceByUsername.keySet());
    }

    private void publishPresenceChanged(String userId, String username, boolean online, LocalDateTime lastSeenAt) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            payload.put("online", online);
            payload.put("lastSeenAt", lastSeenAt != null ? lastSeenAt.toString() : null);

            socketService.sendToAll(SocketEventDTO.of("USER_PRESENCE_CHANGED", userId, payload));
        } catch (Exception e) {
            log.warn("Failed to publish USER_PRESENCE_CHANGED for {}: {}", username, e.getMessage());
        }
    }

    private static class PresenceEntry {
        private String userId;
        private String username;
        private int connectionCount;
        private LocalDateTime lastSeenAt;
    }
}
