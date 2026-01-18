package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.SocketEventDTO;
import edu.iuh.fit.se.commonservice.service.SocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for other services to trigger socket events
 * This allows MessageService (and other services) to emit socket events via HTTP calls
 */
@Slf4j
@RestController
@RequestMapping("/api/socket")
@RequiredArgsConstructor
public class SocketEventController {

    private final SocketService socketService;

    /**
     * Emit socket event to specific user
     * POST /api/socket/emit/user/{username}
     */
    @PostMapping("/emit/user/{username}")
    public ResponseEntity<Void> emitToUser(
            @PathVariable String username,
            @RequestBody SocketEventDTO event
    ) {
        log.info("📨 Received request to emit {} event to user: {}", event.getType(), username);
        socketService.sendNotification(username, event);
        return ResponseEntity.ok().build();
    }

    /**
     * Emit socket event to all users
     * POST /api/socket/emit/all
     */
    @PostMapping("/emit/all")
    public ResponseEntity<Void> emitToAll(@RequestBody SocketEventDTO event) {
        log.info("📨 Received request to emit {} event to all users", event.getType());
        log.info("🔍 Event data type: {}, data: {}", 
            event.getData() != null ? event.getData().getClass().getSimpleName() : "null",
            event.getData());
        socketService.sendToAll(event);
        return ResponseEntity.ok().build();
    }

    /**
     * Emit socket event to specific topic
     * POST /api/socket/emit/topic/{topic}
     */
    @PostMapping("/emit/topic/{topic}")
    public ResponseEntity<Void> emitToTopic(
            @PathVariable String topic,
            @RequestBody SocketEventDTO event
    ) {
        log.info("📨 Received request to emit {} event to topic: {}", event.getType(), topic);
        socketService.sendToTopic(topic, event);
        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint
     * GET /api/socket/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Socket event service is running");
    }
}
