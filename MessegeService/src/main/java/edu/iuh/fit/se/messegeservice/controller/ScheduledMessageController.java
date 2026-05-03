package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.CreateScheduledMessageRequest;
import edu.iuh.fit.se.messegeservice.dto.ScheduledMessageDTO;
import edu.iuh.fit.se.messegeservice.service.ScheduledMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scheduled-messages")
@RequiredArgsConstructor
public class ScheduledMessageController {

    private final ScheduledMessageService scheduledMessageService;

    /**
     * Create a scheduled message in a conversation.
     * POST /scheduled-messages/conversation/{conversationId}
     */
    @PostMapping("/conversation/{conversationId}")
    public ResponseEntity<ScheduledMessageDTO> createScheduledMessage(
            @PathVariable String conversationId,
            @RequestBody CreateScheduledMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledMessageService.createScheduledMessage(conversationId, request));
    }

    /**
     * Get all pending scheduled messages for a conversation (sender-scoped).
     * GET /scheduled-messages/conversation/{conversationId}?userId=...
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<ScheduledMessageDTO>> getScheduledMessages(
            @PathVariable String conversationId,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(scheduledMessageService.getScheduledMessages(conversationId, userId));
    }

    /**
     * Cancel a scheduled message.
     * DELETE /scheduled-messages/{id}?userId=...
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelScheduledMessage(
            @PathVariable String id,
            @RequestParam String userId
    ) {
        scheduledMessageService.cancelScheduledMessage(id, userId);
        return ResponseEntity.noContent().build();
    }
}
