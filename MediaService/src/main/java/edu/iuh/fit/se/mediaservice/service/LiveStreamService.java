package edu.iuh.fit.se.mediaservice.service;

import edu.iuh.fit.se.mediaservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamApproveRequest;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamDTO;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamKickRequest;
import edu.iuh.fit.se.mediaservice.dto.SocketEventDTO;
import edu.iuh.fit.se.mediaservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.mediaservice.model.LiveStream;
import edu.iuh.fit.se.mediaservice.repository.LiveStreamRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveStreamService {

    private final LiveStreamRepository liveStreamRepository;
    private final SocketEmitterService socketEmitterService;
    private final LiveKitRoomAdminService liveKitRoomAdminService;
    private final S3StorageService s3StorageService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final VipService vipService;

    @Value("${livekit.api.key:devkey}")
    private String livekitApiKey;

    @Value("${livekit.api.secret:secret12345678901234567890123456789012}")
    private String livekitApiSecret;

    @Value("${livekit.url:wss://livestream-demo.livekit.cloud}")
    private String livekitUrl;

    public LiveStreamDTO createStream(String streamerId, String streamerName,
                                      String streamerAvatar, String title,
                                      String description,
                                      Boolean requiresApproval,
                                      String thumbnailUrl) {
        if (streamerId == null || streamerId.isBlank()) {
            throw new IllegalArgumentException("streamerId is required");
        }

        List<LiveStream> active = liveStreamRepository
                .findByStreamerIdAndStatusIn(streamerId, List.of("PENDING", "LIVE"));
        if (!active.isEmpty()) {
            throw new IllegalStateException("You already have an active stream. End it first.");
        }

        String roomName = "live-" + UUID.randomUUID().toString().substring(0, 8);
        String streamKey = "sk-" + UUID.randomUUID().toString().replace("-", "");
        String resolvedStreamerName = StringUtils.hasText(streamerName) ? streamerName.trim() : "Unknown";
        String resolvedTitle = StringUtils.hasText(title)
                ? title.trim()
                : "Phòng live của " + resolvedStreamerName;

        LiveStream stream = new LiveStream();
        stream.setStreamerId(streamerId);
        stream.setStreamerName(resolvedStreamerName);
        stream.setStreamerAvatar(streamerAvatar);
        stream.setTitle(resolvedTitle);
        stream.setDescription(description);
        stream.setRoomName(roomName);
        stream.setStreamKey(streamKey);
        stream.setStatus("LIVE");
        stream.setViewerCount(0);
        stream.setViewerIds(new ArrayList<>());
        stream.setRequiresApproval(Boolean.TRUE.equals(requiresApproval));
        List<String> approved = new ArrayList<>();
        approved.add(streamerId);
        stream.setApprovedViewerIds(approved);
        if (StringUtils.hasText(thumbnailUrl)) {
            stream.setThumbnailUrl(thumbnailUrl.trim());
        }

        // Set VIP-based duration limits
        int vipLevel = vipService.getVipLevel(streamerId);
        int maxMinutes = vipService.getMaxLiveDuration(streamerId);
        stream.setVipLevel(vipLevel);
        stream.setMaxLiveDurationMinutes(maxMinutes);

        stream.setCreatedAt(LocalDateTime.now());
        stream.setStartedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());

        LiveStream saved = liveStreamRepository.save(stream);
        log.info("🎥 LiveStream created: id={}, room={}, streamKey={}, title={}, streamer={}",
                saved.getId(), roomName, streamKey, resolvedTitle, resolvedStreamerName);

        emitLiveEvent(SocketEventTypes.LIVE_STARTED, saved);

        LiveStreamDTO dto = toDTO(saved, false);
        dto.setLivekitToken(generateToken(saved, streamerId, resolvedStreamerName, true, true));
        dto.setLivekitUrl(livekitUrl);
        dto.setIsHost(true);
        dto.setCanSubscribe(true);
        dto.setJoinStatus("APPROVED");
        return dto;
    }

    public LiveStreamDTO getToken(String roomName, String userId, String userName) {
        LiveStream stream = liveStreamRepository.findByRoomName(roomName)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found for room: " + roomName));
        stream = normalizeOrphanLiveStream(stream);

        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }

        boolean isHost = stream.getStreamerId().equals(userId);
        List<String> approved = stream.getApprovedViewerIds() != null
                ? stream.getApprovedViewerIds()
                : new ArrayList<>();
        boolean canSubscribe = isHost || !stream.isRequiresApproval() || approved.contains(userId);
        boolean canPublish = isHost;
        String displayName = userName != null && !userName.isBlank() ? userName : "User";

        String token = generateToken(stream, userId, displayName, canPublish, canSubscribe);

        LiveStreamDTO dto = toDTO(stream, stream.getStreamerId().equals(userId));
        dto.setLivekitToken(token);
        dto.setLivekitUrl(livekitUrl);
        dto.setIsHost(isHost);
        dto.setCanSubscribe(canSubscribe);
        dto.setJoinStatus(canSubscribe ? "APPROVED" : "WAITING");
        return dto;
    }

    private String generateToken(LiveStream stream, String participantId, String participantName,
                                 boolean canPublish, boolean canSubscribe) {
        long nowSec = System.currentTimeMillis() / 1000;
        long expSec = nowSec + 6 * 3600;

        boolean isHost = stream.getStreamerId().equals(participantId);
        String metaStatus = canSubscribe ? "approved" : "waiting";
        String metadataJson = String.format(
                "{\"isHost\":%s,\"status\":\"%s\"}",
                isHost ? "true" : "false",
                metaStatus
        );

        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("room", stream.getRoomName());
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", canPublish);
        videoGrant.put("canSubscribe", canSubscribe);
        videoGrant.put("canPublishData", true);

        SecretKey key = Keys.hmacShaKeyFor(livekitApiSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .issuer(livekitApiKey)
                .subject(participantId)
                .claim("name", participantName)
                .claim("metadata", metadataJson)
                .claim("video", videoGrant)
                .issuedAt(new Date(nowSec * 1000))
                .expiration(new Date(expSec * 1000))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
    }

    public LiveStreamDTO endStream(String streamId, String requesterId) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));

        if (!stream.getStreamerId().equals(requesterId)) {
            throw new IllegalArgumentException("Only the streamer can end the stream");
        }

        stream.setStatus("ENDED");
        stream.setEndedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());
        stream.setViewerCount(0);
        stream.setViewerIds(new ArrayList<>());
        LiveStream saved = liveStreamRepository.save(stream);

        liveKitRoomAdminService.deleteRoom(stream.getRoomName());

        emitLiveEvent(SocketEventTypes.LIVE_ENDED, saved);
        return toDTO(saved, false);
    }

    public LiveStreamDTO joinStream(String streamId, String viewerId) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        stream = normalizeOrphanLiveStream(stream);

        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }

        List<String> viewers = stream.getViewerIds();
        if (viewers == null) viewers = new ArrayList<>();
        if (!viewers.contains(viewerId)) {
            viewers.add(viewerId);
            stream.setViewerIds(viewers);
            stream.setViewerCount(viewers.size());
            stream.setUpdatedAt(LocalDateTime.now());
            liveStreamRepository.save(stream);
        }

        emitViewerCount(stream);
        return toDTO(stream, stream.getStreamerId().equals(viewerId));
    }

    public LiveStreamDTO leaveStream(String streamId, String viewerId) {
        LiveStream stream = liveStreamRepository.findById(streamId).orElse(null);
        if (stream == null) return null;

        List<String> viewers = stream.getViewerIds();
        if (viewers != null && viewers.remove(viewerId)) {
            stream.setViewerIds(viewers);
            stream.setViewerCount(viewers.size());
            stream.setUpdatedAt(LocalDateTime.now());
            liveStreamRepository.save(stream);
            emitViewerCount(stream);
        }

        return toDTO(stream, false);
    }

    public List<LiveStreamDTO> getActiveStreams() {
        return liveStreamRepository.findByStatusOrderByStartedAtDesc("LIVE")
                .stream()
                .map(this::normalizeOrphanLiveStream)
                .filter(s -> "LIVE".equals(s.getStatus()))
                .map(s -> toDTO(s, false))
                .toList();
    }

    public LiveStreamDTO getStreamById(String streamId, String requesterId) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        stream = normalizeOrphanLiveStream(stream);
        boolean includeApproved = requesterId != null && requesterId.equals(stream.getStreamerId());
        LiveStreamDTO dto = toDTO(stream, includeApproved);
        if (requesterId != null && !requesterId.isBlank()) {
            dto.setJoinStatus(computeJoinStatus(stream, requesterId));
        }
        return dto;
    }

    private String computeJoinStatus(LiveStream stream, String userId) {
        if (!"LIVE".equals(stream.getStatus())) {
            return "ENDED";
        }
        if (stream.getStreamerId().equals(userId)) {
            return "APPROVED";
        }
        List<String> approved = stream.getApprovedViewerIds() != null
                ? stream.getApprovedViewerIds()
                : List.of();
        if (stream.isRequiresApproval() && !approved.contains(userId)) {
            return "WAITING";
        }
        return "APPROVED";
    }

    public List<LiveStreamDTO> getMyStreams(String userId) {
        return liveStreamRepository.findByStreamerIdOrderByCreatedAtDesc(userId)
                .stream().map(s -> toDTO(s, true)).toList();
    }

    public LiveStreamDTO getMyActiveStream(String userId) {
        List<LiveStream> active = liveStreamRepository
                .findByStreamerIdAndStatusIn(userId, List.of("PENDING", "LIVE"));
        if (active.isEmpty()) return null;
        LiveStream stream = normalizeOrphanLiveStream(active.get(0));
        if (!"LIVE".equals(stream.getStatus()) && !"PENDING".equals(stream.getStatus())) {
            return null;
        }
        return toDTO(stream, true);
    }

    public LiveStreamDTO updateSettings(String streamId, String hostUserId, boolean requiresApproval) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        if (!stream.getStreamerId().equals(hostUserId)) {
            throw new IllegalArgumentException("Only the streamer can change settings");
        }
        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }
        stream.setRequiresApproval(requiresApproval);
        stream.setUpdatedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);
        return toDTO(stream, true);
    }

    public LiveStreamDTO approveViewer(String streamId, LiveStreamApproveRequest req) {
        if (req.getHostUserId() == null || req.getViewerUserId() == null) {
            throw new IllegalArgumentException("hostUserId and viewerUserId are required");
        }
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        if (!stream.getStreamerId().equals(req.getHostUserId())) {
            throw new IllegalArgumentException("Only the streamer can approve viewers");
        }
        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }
        List<String> approved = stream.getApprovedViewerIds() != null
                ? new ArrayList<>(stream.getApprovedViewerIds())
                : new ArrayList<>();
        if (!approved.contains(req.getViewerUserId())) {
            approved.add(req.getViewerUserId());
        }
        stream.setApprovedViewerIds(approved);
        stream.setUpdatedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);

        liveKitRoomAdminService.approveParticipantSubscribe(stream.getRoomName(), req.getViewerUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("streamId", stream.getId());
        payload.put("roomName", stream.getRoomName());
        socketEmitterService.emitToUserById(
                req.getViewerUserId(),
                SocketEventDTO.of(SocketEventTypes.LIVE_VIEWER_APPROVED, req.getViewerUserId(), payload)
        );
        return toDTO(stream, true);
    }

    public LiveStreamDTO kickViewer(String streamId, LiveStreamKickRequest req) {
        if (req.getHostUserId() == null || req.getParticipantUserId() == null) {
            throw new IllegalArgumentException("hostUserId and participantUserId are required");
        }
        if (req.getHostUserId().equals(req.getParticipantUserId())) {
            throw new IllegalArgumentException("Cannot kick yourself");
        }
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        if (!stream.getStreamerId().equals(req.getHostUserId())) {
            throw new IllegalArgumentException("Only the streamer can kick participants");
        }
        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }

        List<String> viewers = stream.getViewerIds();
        if (viewers != null) {
            viewers.remove(req.getParticipantUserId());
            stream.setViewerIds(viewers);
            stream.setViewerCount(viewers.size());
        }
        List<String> approved = stream.getApprovedViewerIds();
        if (approved != null) {
            approved.remove(req.getParticipantUserId());
            stream.setApprovedViewerIds(approved);
        }
        stream.setUpdatedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);

        liveKitRoomAdminService.removeParticipant(stream.getRoomName(), req.getParticipantUserId());
        emitViewerCount(stream);

        Map<String, Object> payload = new HashMap<>();
        payload.put("streamId", stream.getId());
        payload.put("reason", "KICKED_BY_HOST");
        socketEmitterService.emitToUserById(
                req.getParticipantUserId(),
                SocketEventDTO.of(SocketEventTypes.LIVE_KICKED, req.getParticipantUserId(), payload)
        );
        return toDTO(stream, true);
    }

    public void broadcastLiveChat(String streamId, String userId, String userName, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        if (!"LIVE".equals(stream.getStatus())) {
            throw new IllegalStateException("Stream is not live");
        }
        String trimmed = content.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Message too long");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("streamId", streamId);
        payload.put("userId", userId);
        payload.put("userName", userName != null && !userName.isBlank() ? userName : "User");
        payload.put("content", trimmed);
        payload.put("roomName", stream.getRoomName());

        try {
            SocketEventDTO event = SocketEventDTO.of(SocketEventTypes.LIVE_CHAT, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_CHAT: {}", e.getMessage());
        }
    }

    public LiveStreamDTO uploadThumbnail(String streamId, String hostUserId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        if (!stream.getStreamerId().equals(hostUserId)) {
            throw new IllegalArgumentException("Only the streamer can upload thumbnail");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Thumbnail must be an image");
        }

        String url;
        if (s3StorageService.isEnabled()) {
            url = s3StorageService.upload(file, "ttvv/live-thumbnails");
        } else if (cloudinaryStorageService.isEnabled()) {
            url = cloudinaryStorageService.upload(file, "ttvv/live-thumbnails");
        } else {
            throw new IllegalStateException("No file storage configured (S3 or Cloudinary)");
        }

        stream.setThumbnailUrl(url);
        stream.setUpdatedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);
        return toDTO(stream, true);
    }

    private void emitViewerCount(LiveStream stream) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("viewerCount", stream.getViewerCount());
            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_VIEWER_COUNT, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_VIEWER_COUNT: {}", e.getMessage());
        }
    }

    private void emitLiveEvent(String eventType, LiveStream stream) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("streamerId", stream.getStreamerId());
            payload.put("streamerName", stream.getStreamerName());
            payload.put("title", stream.getTitle());
            payload.put("roomName", stream.getRoomName());
            payload.put("thumbnailUrl", stream.getThumbnailUrl());
            SocketEventDTO event = SocketEventDTO.of(eventType, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit {}: {}", eventType, e.getMessage());
        }
    }

    /**
     * Auto-close orphan streams when host is no longer in LiveKit room.
     * Adds a short grace period to avoid ending right after stream creation.
     */
    private LiveStream normalizeOrphanLiveStream(LiveStream stream) {
        if (stream == null || !"LIVE".equals(stream.getStatus())) {
            return stream;
        }
        if (!liveKitRoomAdminService.isEnabled()) {
            return stream;
        }
        if (stream.getRoomName() == null || stream.getRoomName().isBlank()) {
            return closeAsOrphan(stream, "missing roomName");
        }
        if (!isOlderThan(stream, 45)) {
            return stream;
        }

        List<livekit.LivekitModels.ParticipantInfo> participants =
                liveKitRoomAdminService.listParticipants(stream.getRoomName());
        boolean hostPresent = participants.stream()
                .anyMatch(p -> stream.getStreamerId().equals(p.getIdentity()));

        if (hostPresent) {
            return stream;
        }
        return closeAsOrphan(stream, "host not present in room");
    }

    private LiveStream closeAsOrphan(LiveStream stream, String reason) {
        stream.setStatus("ENDED");
        stream.setEndedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());
        stream.setViewerCount(0);
        stream.setViewerIds(new ArrayList<>());
        LiveStream saved = liveStreamRepository.save(stream);
        liveKitRoomAdminService.deleteRoom(stream.getRoomName());
        emitLiveEvent(SocketEventTypes.LIVE_ENDED, saved);
        log.warn("Auto-ended orphan stream {} (room={}): {}", saved.getId(), saved.getRoomName(), reason);
        return saved;
    }

    private boolean isOlderThan(LiveStream stream, int seconds) {
        LocalDateTime base = stream.getStartedAt() != null ? stream.getStartedAt() : stream.getCreatedAt();
        if (base == null) return true;
        return base.isBefore(LocalDateTime.now().minusSeconds(seconds));
    }

    private LiveStreamDTO toDTO(LiveStream stream, boolean includeApprovedList) {
        LiveStreamDTO dto = new LiveStreamDTO();
        dto.setId(stream.getId());
        dto.setStreamerId(stream.getStreamerId());
        dto.setStreamerName(stream.getStreamerName());
        dto.setStreamerAvatar(stream.getStreamerAvatar());
        dto.setTitle(stream.getTitle());
        dto.setDescription(stream.getDescription());
        dto.setRoomName(stream.getRoomName());
        dto.setStatus(stream.getStatus());
        dto.setThumbnailUrl(stream.getThumbnailUrl());
        dto.setViewerCount(stream.getViewerCount());
        dto.setViewerIds(stream.getViewerIds());
        dto.setChatConversationId(stream.getChatConversationId());
        dto.setRequiresApproval(stream.isRequiresApproval());
        dto.setVipLevel(stream.getVipLevel());
        dto.setMaxLiveDurationMinutes(stream.getMaxLiveDurationMinutes());
        if (includeApprovedList) {
            dto.setApprovedViewerIds(stream.getApprovedViewerIds());
        }
        dto.setStartedAt(stream.getStartedAt());
        dto.setEndedAt(stream.getEndedAt());
        dto.setCreatedAt(stream.getCreatedAt());
        return dto;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ═══ Scheduled: Auto-end streams that exceed VIP time limit ═══════════════
    // ═══════════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 30_000) // every 30 seconds
    public void autoEndExpiredStreams() {
        List<LiveStream> liveStreams = liveStreamRepository.findByStatusOrderByStartedAtDesc("LIVE");
        LocalDateTime now = LocalDateTime.now();

        for (LiveStream stream : liveStreams) {
            int maxMinutes = stream.getMaxLiveDurationMinutes();
            if (maxMinutes <= 0) continue; // unlimited

            LocalDateTime startedAt = stream.getStartedAt();
            if (startedAt == null) continue;

            long elapsedMinutes = java.time.Duration.between(startedAt, now).toMinutes();
            if (elapsedMinutes >= maxMinutes) {
                log.info("⏰ Auto-ending stream {} (VIP {}): {}min >= {}min limit",
                        stream.getId(), stream.getVipLevel(), elapsedMinutes, maxMinutes);

                stream.setStatus("ENDED");
                stream.setEndedAt(now);
                stream.setUpdatedAt(now);
                stream.setViewerCount(0);
                stream.setViewerIds(new ArrayList<>());
                liveStreamRepository.save(stream);

                liveKitRoomAdminService.deleteRoom(stream.getRoomName());

                // Emit time-expired event so all clients show the modal
                emitTimeExpiredEvent(stream);
                emitLiveEvent(SocketEventTypes.LIVE_ENDED, stream);
            }
        }
    }

    private void emitTimeExpiredEvent(LiveStream stream) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("roomName", stream.getRoomName());
            payload.put("vipLevel", stream.getVipLevel());
            payload.put("maxMinutes", stream.getMaxLiveDurationMinutes());
            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_TIME_EXPIRED, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_TIME_EXPIRED: {}", e.getMessage());
        }
    }
}
