package edu.iuh.fit.se.mediaservice.controller;

import edu.iuh.fit.se.mediaservice.dto.CreateStreamRequest;
import edu.iuh.fit.se.mediaservice.dto.LiveChatRequest;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamApproveRequest;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamDTO;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamKickRequest;
import edu.iuh.fit.se.mediaservice.dto.LiveStreamSettingsRequest;
import edu.iuh.fit.se.mediaservice.service.LiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/livestream")
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    // ── Create a new stream session ──────────────────────────────────────
    @PostMapping("/create")
    public ResponseEntity<LiveStreamDTO> createStream(@RequestBody CreateStreamRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(liveStreamService.createStream(req.getUserId(), req.getStreamerName(),
                        req.getStreamerAvatar(), req.getTitle(), req.getDescription(),
                        req.getRequiresApproval(), req.getThumbnailUrl()));
    }

    // ── Get LiveKit token for joining ────────────────────────────────────
    @GetMapping("/token")
    public ResponseEntity<LiveStreamDTO> getToken(
            @RequestParam String roomName,
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "User") String userName
    ) {
        return ResponseEntity.ok(liveStreamService.getToken(roomName, userId, userName));
    }

    // ── Get all active (LIVE) streams ────────────────────────────────────
    @GetMapping("/active")
    public ResponseEntity<List<LiveStreamDTO>> getActiveStreams() {
        return ResponseEntity.ok(liveStreamService.getActiveStreams());
    }

    // ── Get stream by ID ─────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<LiveStreamDTO> getStreamById(
            @PathVariable String id,
            @RequestParam(required = false) String userId
    ) {
        return ResponseEntity.ok(liveStreamService.getStreamById(id, userId));
    }

    // ── Get my active/pending stream ─────────────────────────────────────
    @GetMapping("/my/{userId}")
    public ResponseEntity<LiveStreamDTO> getMyActiveStream(@PathVariable String userId) {
        LiveStreamDTO stream = liveStreamService.getMyActiveStream(userId);
        if (stream == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(stream);
    }

    // ── Get all my streams (history) ─────────────────────────────────────
    @GetMapping("/my/{userId}/history")
    public ResponseEntity<List<LiveStreamDTO>> getMyStreams(@PathVariable String userId) {
        return ResponseEntity.ok(liveStreamService.getMyStreams(userId));
    }

    // ── End a stream ─────────────────────────────────────────────────────
    @PostMapping("/{id}/end")
    public ResponseEntity<LiveStreamDTO> endStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(liveStreamService.endStream(id, userId));
    }

    // ── Viewer joins a stream ────────────────────────────────────────────
    @PostMapping("/{id}/join")
    public ResponseEntity<LiveStreamDTO> joinStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(liveStreamService.joinStream(id, userId));
    }

    // ── Viewer leaves a stream ───────────────────────────────────────────
    @PostMapping("/{id}/leave")
    public ResponseEntity<LiveStreamDTO> leaveStream(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        LiveStreamDTO result = liveStreamService.leaveStream(id, userId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/settings")
    public ResponseEntity<LiveStreamDTO> updateSettings(
            @PathVariable String id,
            @RequestBody LiveStreamSettingsRequest body
    ) {
        return ResponseEntity.ok(liveStreamService.updateSettings(id, body.getHostUserId(), body.isRequiresApproval()));
    }

    @PostMapping("/{id}/approve-viewer")
    public ResponseEntity<LiveStreamDTO> approveViewer(
            @PathVariable String id,
            @RequestBody LiveStreamApproveRequest body
    ) {
        return ResponseEntity.ok(liveStreamService.approveViewer(id, body));
    }

    @PostMapping("/{id}/kick")
    public ResponseEntity<LiveStreamDTO> kickViewer(
            @PathVariable String id,
            @RequestBody LiveStreamKickRequest body
    ) {
        return ResponseEntity.ok(liveStreamService.kickViewer(id, body));
    }

    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<LiveStreamDTO> uploadThumbnail(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(liveStreamService.uploadThumbnail(id, userId, file));
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<Void> sendLiveChat(@PathVariable String id, @RequestBody LiveChatRequest body) {
        liveStreamService.broadcastLiveChat(id, body.getUserId(), body.getUserName(), body.getContent());
        return ResponseEntity.ok().build();
    }
}
