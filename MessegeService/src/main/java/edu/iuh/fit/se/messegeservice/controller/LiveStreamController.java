package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.LiveStreamDTO;
import edu.iuh.fit.se.messegeservice.service.LiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/livestream")
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    // ── Create a new stream session ──────────────────────────────────────
    @PostMapping("/create")
    public ResponseEntity<LiveStreamDTO> createStream(
            @RequestParam String userId,
            @RequestParam(required = false) String streamerName,
            @RequestParam(required = false) String streamerAvatar,
            @RequestParam String title,
            @RequestParam(required = false) String description
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveStreamService.createStream(userId, streamerName, streamerAvatar, title, description));
    }

    // ── Get all active (LIVE) streams ──────────────────────────────────────
    @GetMapping("/active")
    public ResponseEntity<List<LiveStreamDTO>> getActiveStreams() {
        return ResponseEntity.ok(liveStreamService.getActiveStreams());
    }

    // ── Get stream by ID ──────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<LiveStreamDTO> getStreamById(
            @PathVariable String id,
            @RequestParam(required = false) String userId
    ) {
        return ResponseEntity.ok(liveStreamService.getStreamById(id, userId));
    }

    // ── Get my active/pending stream ──────────────────────────────────────
    @GetMapping("/my/{userId}")
    public ResponseEntity<LiveStreamDTO> getMyActiveStream(@PathVariable String userId) {
        LiveStreamDTO stream = liveStreamService.getMyActiveStream(userId);
        if (stream == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stream);
    }

    // ── Get all my streams (history) ──────────────────────────────────────
    @GetMapping("/my/{userId}/history")
    public ResponseEntity<List<LiveStreamDTO>> getMyStreams(@PathVariable String userId) {
        return ResponseEntity.ok(liveStreamService.getMyStreams(userId));
    }

    // ── End a stream ──────────────────────────────────────────────────────
    @PostMapping("/{id}/end")
    public ResponseEntity<LiveStreamDTO> endStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(liveStreamService.endStream(id, userId));
    }

    // ── Viewer joins a stream ──────────────────────────────────────────────
    @PostMapping("/{id}/join")
    public ResponseEntity<LiveStreamDTO> joinStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(liveStreamService.joinStream(id, userId));
    }

    // ── Viewer leaves a stream ──────────────────────────────────────────────
    @PostMapping("/{id}/leave")
    public ResponseEntity<LiveStreamDTO> leaveStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        LiveStreamDTO result = liveStreamService.leaveStream(id, userId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    // ── RTMP Server Webhooks (internal, called by Node Media Server) ──────

    @PostMapping("/hook/start")
    public ResponseEntity<Void> hookStreamStart(@RequestBody Map<String, String> body) {
        String streamKey = body.get("streamKey");
        if (streamKey == null || streamKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        liveStreamService.onStreamStartHook(streamKey);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/hook/end")
    public ResponseEntity<Void> hookStreamEnd(@RequestBody Map<String, String> body) {
        String streamKey = body.get("streamKey");
        if (streamKey == null || streamKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        liveStreamService.onStreamEndHook(streamKey);
        return ResponseEntity.ok().build();
    }
}
