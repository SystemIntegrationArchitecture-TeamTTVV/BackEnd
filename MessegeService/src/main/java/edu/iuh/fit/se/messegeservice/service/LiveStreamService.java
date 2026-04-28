package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.LiveStreamDTO;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.model.LiveStream;
import edu.iuh.fit.se.messegeservice.repository.LiveStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveStreamService {

    private final LiveStreamRepository liveStreamRepository;
    private final SocketEmitterService socketEmitterService;

    @Value("${livestream.rtmp.url:rtmp://localhost:1935/live}")
    private String rtmpBaseUrl;

    @Value("${livestream.hls.base-url:http://localhost:8888/live}")
    private String hlsBaseUrl;

    // ── Create a new live stream session ──────────────────────────────────
    public LiveStreamDTO createStream(String streamerId, String streamerName, String streamerAvatar,
                                       String title, String description) {
        if (streamerId == null || streamerId.isBlank()) {
            throw new IllegalArgumentException("streamerId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        // Check if user already has an active stream
        List<LiveStream> activeStreams = liveStreamRepository
                .findByStreamerIdAndStatusIn(streamerId, List.of("PENDING", "LIVE"));
        if (!activeStreams.isEmpty()) {
            throw new IllegalStateException("You already have an active stream. End it first.");
        }

        // Generate unique stream key
        String streamKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        LiveStream stream = new LiveStream();
        stream.setStreamerId(streamerId);
        stream.setStreamerName(streamerName != null ? streamerName : "Unknown");
        stream.setStreamerAvatar(streamerAvatar);
        stream.setTitle(title);
        stream.setDescription(description);
        stream.setStreamKey(streamKey);
        stream.setStatus("PENDING");
        stream.setHlsUrl(hlsBaseUrl + "/" + streamKey + "/index.m3u8");
        stream.setViewerCount(0);
        stream.setViewerIds(new ArrayList<>());
        stream.setCreatedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());

        LiveStream saved = liveStreamRepository.save(stream);
        log.info("🎥 LiveStream created: id={}, streamKey={}, streamer={}", saved.getId(), streamKey, streamerName);

        return toDTO(saved, true); // true = owner view (include streamKey)
    }

    // ── RTMP webhook: OBS started pushing ──────────────────────────────────
    public void onStreamStartHook(String streamKey) {
        LiveStream stream = liveStreamRepository.findByStreamKey(streamKey)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found for key: " + streamKey));

        stream.setStatus("LIVE");
        stream.setStartedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());
        liveStreamRepository.save(stream);

        log.info("🔴 LiveStream STARTED: id={}, streamer={}", stream.getId(), stream.getStreamerName());

        // Emit LIVE_STARTED to all connected users
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("streamerId", stream.getStreamerId());
            payload.put("streamerName", stream.getStreamerName());
            payload.put("streamerAvatar", stream.getStreamerAvatar());
            payload.put("title", stream.getTitle());
            payload.put("hlsUrl", stream.getHlsUrl());

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_STARTED, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_STARTED: {}", e.getMessage());
        }
    }

    // ── RTMP webhook: OBS stopped pushing ──────────────────────────────────
    public void onStreamEndHook(String streamKey) {
        LiveStream stream = liveStreamRepository.findByStreamKey(streamKey).orElse(null);
        if (stream == null) {
            log.warn("Stream end hook for unknown key: {}", streamKey);
            return;
        }

        stream.setStatus("ENDED");
        stream.setEndedAt(LocalDateTime.now());
        stream.setUpdatedAt(LocalDateTime.now());
        stream.setViewerCount(0);
        stream.setViewerIds(new ArrayList<>());
        liveStreamRepository.save(stream);

        log.info("⬛ LiveStream ENDED: id={}, streamer={}", stream.getId(), stream.getStreamerName());

        // Emit LIVE_ENDED to all
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("streamerId", stream.getStreamerId());

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_ENDED, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_ENDED: {}", e.getMessage());
        }
    }

    // ── End stream manually (from dashboard) ──────────────────────────────
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

        // Emit LIVE_ENDED
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("streamId", stream.getId());
            payload.put("streamerId", stream.getStreamerId());

            SocketEventDTO event = SocketEventDTO.of(
                    SocketEventTypes.LIVE_ENDED, stream.getStreamerId(), payload);
            socketEmitterService.emitToAll(event);
        } catch (Exception e) {
            log.error("Failed to emit LIVE_ENDED: {}", e.getMessage());
        }

        return toDTO(saved, true);
    }

    // ── Viewer joins a stream ──────────────────────────────────────────────
    public LiveStreamDTO joinStream(String streamId, String viewerId) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));

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

    // ── Viewer leaves a stream ──────────────────────────────────────────────
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

    // ── Get active (LIVE) streams ──────────────────────────────────────────
    public List<LiveStreamDTO> getActiveStreams() {
        return liveStreamRepository.findByStatusOrderByStartedAtDesc("LIVE")
                .stream()
                .map(s -> toDTO(s, false))
                .toList();
    }

    // ── Get stream by ID ──────────────────────────────────────────────────
    public LiveStreamDTO getStreamById(String streamId, String requesterId) {
        LiveStream stream = liveStreamRepository.findById(streamId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream not found: " + streamId));
        boolean isOwner = requesterId != null && requesterId.equals(stream.getStreamerId());
        return toDTO(stream, isOwner);
    }

    // ── Get user's streams ──────────────────────────────────────────────────
    public List<LiveStreamDTO> getMyStreams(String userId) {
        return liveStreamRepository.findByStreamerIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(s -> toDTO(s, true))
                .toList();
    }

    // ── Get user's active/pending stream ──────────────────────────────────
    public LiveStreamDTO getMyActiveStream(String userId) {
        List<LiveStream> active = liveStreamRepository
                .findByStreamerIdAndStatusIn(userId, List.of("PENDING", "LIVE"));
        if (active.isEmpty()) return null;
        return toDTO(active.get(0), true);
    }

    // ── Emit viewer count update ──────────────────────────────────────────
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

    // ── Convert to DTO ──────────────────────────────────────────────────────
    private LiveStreamDTO toDTO(LiveStream stream, boolean includeStreamKey) {
        LiveStreamDTO dto = new LiveStreamDTO();
        dto.setId(stream.getId());
        dto.setStreamerId(stream.getStreamerId());
        dto.setStreamerName(stream.getStreamerName());
        dto.setStreamerAvatar(stream.getStreamerAvatar());
        dto.setTitle(stream.getTitle());
        dto.setDescription(stream.getDescription());
        dto.setStatus(stream.getStatus());
        dto.setHlsUrl(stream.getHlsUrl());
        dto.setThumbnailUrl(stream.getThumbnailUrl());
        dto.setViewerCount(stream.getViewerCount());
        dto.setViewerIds(stream.getViewerIds());
        dto.setChatConversationId(stream.getChatConversationId());
        dto.setStartedAt(stream.getStartedAt());
        dto.setEndedAt(stream.getEndedAt());
        dto.setCreatedAt(stream.getCreatedAt());

        // Only expose stream key + RTMP URL to the owner
        if (includeStreamKey) {
            dto.setStreamKey(stream.getStreamKey());
            dto.setRtmpUrl(rtmpBaseUrl);
        }

        return dto;
    }
}
