package edu.iuh.fit.se.authservice.service;

import edu.iuh.fit.se.authservice.dto.DeviceSessionDTO;
import edu.iuh.fit.se.authservice.entity.DeviceSessionEntity;
import edu.iuh.fit.se.authservice.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final DeviceSessionRepository deviceSessionRepository;
    private final TokenStoreService tokenStoreService;

    @Value("${app.features.device-sessions.enabled:false}")
    private boolean deviceSessionsEnabled;

    public boolean isEnabled() {
        return deviceSessionsEnabled;
    }

    @Transactional
    public void onLogin(String userId, String username, String refreshToken, String userAgent, String ipAddress) {
        if (!deviceSessionsEnabled || refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        DeviceSessionEntity entity = DeviceSessionEntity.builder()
                .userId(userId)
                .username(username)
                .refreshToken(refreshToken)
                .source("LOGIN")
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .revoked(false)
                .lastSeenAt(LocalDateTime.now())
                .build();

        deviceSessionRepository.save(entity);
    }

    @Transactional
    public void onRegister(String userId, String username, String refreshToken, String userAgent, String ipAddress) {
        if (!deviceSessionsEnabled || refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        DeviceSessionEntity entity = DeviceSessionEntity.builder()
                .userId(userId)
                .username(username)
                .refreshToken(refreshToken)
                .source("REGISTER")
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .revoked(false)
                .lastSeenAt(LocalDateTime.now())
                .build();

        deviceSessionRepository.save(entity);
    }

    @Transactional
    public void onRefresh(String oldRefreshToken, String newRefreshToken, String userAgent, String ipAddress) {
        if (!deviceSessionsEnabled || oldRefreshToken == null || oldRefreshToken.isBlank()) {
            return;
        }

        deviceSessionRepository.findByRefreshToken(oldRefreshToken).ifPresent(session -> {
            session.setRefreshToken(newRefreshToken);
            session.setLastSeenAt(LocalDateTime.now());
            if (userAgent != null && !userAgent.isBlank()) {
                session.setUserAgent(userAgent);
            }
            if (ipAddress != null && !ipAddress.isBlank()) {
                session.setIpAddress(ipAddress);
            }
            deviceSessionRepository.save(session);
        });
    }

    @Transactional
    public void onLogout(String refreshToken) {
        if (!deviceSessionsEnabled || refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        deviceSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            deviceSessionRepository.save(session);
        });
    }

    public List<DeviceSessionDTO> getMySessions(String userId) {
        if (!deviceSessionsEnabled) {
            throw new IllegalStateException("Device sessions feature is disabled");
        }

        return deviceSessionRepository.findByUserIdOrderByLastSeenAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        if (!deviceSessionsEnabled) {
            throw new IllegalStateException("Device sessions feature is disabled");
        }

        DeviceSessionEntity session = deviceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!userId.equals(session.getUserId())) {
            throw new RuntimeException("Cannot revoke another user's session");
        }

        if (Boolean.TRUE.equals(session.getRevoked())) {
            return;
        }

        session.setRevoked(true);
        session.setRevokedAt(LocalDateTime.now());
        deviceSessionRepository.save(session);
        tokenStoreService.deleteRefreshToken(session.getRefreshToken());
    }

    @Transactional
    public int revokeAllSessions(String userId) {
        if (!deviceSessionsEnabled) {
            throw new IllegalStateException("Device sessions feature is disabled");
        }

        List<DeviceSessionEntity> activeSessions = deviceSessionRepository.findByUserIdAndRevokedFalse(userId);
        for (DeviceSessionEntity session : activeSessions) {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            tokenStoreService.deleteRefreshToken(session.getRefreshToken());
        }
        deviceSessionRepository.saveAll(activeSessions);
        return activeSessions.size();
    }

    private DeviceSessionDTO toDto(DeviceSessionEntity entity) {
        return DeviceSessionDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .source(entity.getSource())
                .userAgent(entity.getUserAgent())
                .ipAddress(entity.getIpAddress())
                .revoked(entity.getRevoked())
                .lastSeenAt(entity.getLastSeenAt())
                .revokedAt(entity.getRevokedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
