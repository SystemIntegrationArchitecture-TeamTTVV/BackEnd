package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.dto.MessagePageDTO;
import edu.iuh.fit.se.messegeservice.dto.ForwardMessageRequest;
import edu.iuh.fit.se.messegeservice.dto.PollCreateRequest;
import edu.iuh.fit.se.messegeservice.dto.PollVoteRequest;
import edu.iuh.fit.se.messegeservice.dto.SeenEventRequest;
import edu.iuh.fit.se.messegeservice.dto.TypingEventRequest;
import edu.iuh.fit.se.messegeservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageDTO>> getMessagesByConversationId(
            @PathVariable String conversationId,
            @RequestParam(required = false) String userId
    ) {
        return ResponseEntity.ok(messageService.getMessagesByConversationId(conversationId, userId));
    }

    @GetMapping("/conversation/{conversationId}/cursor")
    public ResponseEntity<MessagePageDTO> getMessagesByCursor(
            @PathVariable String conversationId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String userId
    ) {
        return ResponseEntity.ok(messageService.getMessagesByConversationCursor(conversationId, before, limit, userId));
    }

    @GetMapping("/sender/{senderId}")
    public ResponseEntity<List<MessageDTO>> getMessagesBySenderId(@PathVariable String senderId) {
        return ResponseEntity.ok(messageService.getMessagesBySenderId(senderId));
    }

    @GetMapping("/conversation/{conversationId}/count")
    public ResponseEntity<Long> getMessageCountByConversationId(@PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getMessageCountByConversationId(conversationId));
    }

    @GetMapping("/conversation/{conversationId}/pinned")
    public ResponseEntity<List<MessageDTO>> getPinnedMessages(
            @PathVariable String conversationId,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(messageService.getPinnedMessages(conversationId, userId));
    }

    @GetMapping("/conversation/{conversationId}/media")
    public ResponseEntity<List<MessageDTO>> getMediaMessages(
            @PathVariable String conversationId,
            @RequestParam String userId,
            @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(messageService.getMediaMessages(conversationId, userId, type));
    }

    @GetMapping("/conversation/{conversationId}/storage-stats")
    public ResponseEntity<java.util.Map<String, Object>> getStorageStats(
            @PathVariable String conversationId,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(messageService.getStorageStats(conversationId, userId));
    }

    @GetMapping("/conversation/{conversationId}/search")
    public ResponseEntity<List<MessageDTO>> searchMessages(
            @PathVariable String conversationId,
            @RequestParam String keyword,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(messageService.searchMessages(conversationId, keyword, userId));
    }

    @PostMapping("/conversation/{conversationId}/typing")
    public ResponseEntity<Void> typing(
            @PathVariable String conversationId,
            @RequestBody TypingEventRequest request
    ) {
        messageService.emitTypingEvent(conversationId, request.getUserId(), request.isTyping());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/conversation/{conversationId}/seen")
    public ResponseEntity<Void> seen(
            @PathVariable String conversationId,
            @RequestBody edu.iuh.fit.se.messegeservice.dto.SeenEventRequest request
    ) {
        messageService.markSeen(conversationId, request.getUserId(), request.getLastSeenMessageId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/conversation/{conversationId}/delivered")
    public ResponseEntity<Void> delivered(
            @PathVariable String conversationId,
            @RequestBody edu.iuh.fit.se.messegeservice.dto.DeliveredEventRequest request
    ) {
        messageService.markDelivered(conversationId, request.getUserId(), request.getLastDeliveredMessageId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageDTO> getMessageById(@PathVariable String id) {
        return ResponseEntity.ok(messageService.getMessageById(id));
    }

    @PostMapping
    public ResponseEntity<MessageDTO> createMessage(@RequestBody MessageDTO messageDTO) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(messageService.createMessage(messageDTO));
        } catch (Exception e) {
            // Log error for debugging
            System.err.println("❌ Error creating message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<MessageDTO> updateMessage(@PathVariable String id, @RequestBody MessageDTO messageDTO) {
        return ResponseEntity.ok(messageService.updateMessage(id, messageDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable String id,
            @RequestParam String userId) {
        messageService.deleteMessage(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/delete-for-me")
    public ResponseEntity<Void> deleteMessageForMe(
            @PathVariable String id,
            @RequestParam String userId) {
        messageService.deleteMessageForMe(id, userId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/forward")
    public ResponseEntity<MessageDTO> forwardMessage(
            @PathVariable String id,
            @RequestBody ForwardMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.forwardMessage(id, request.getRequesterId(), request.getTargetConversationId(), request.getNote()));
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<MessageDTO> togglePin(@PathVariable String id, @RequestParam String userId) {
        return ResponseEntity.ok(messageService.togglePin(id, userId));
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<MessageDTO> toggleStar(@PathVariable String id, @RequestParam String userId) {
        return ResponseEntity.ok(messageService.toggleStar(id, userId));
    }

    @PostMapping("/{id}/react")
    public ResponseEntity<MessageDTO> toggleReaction(@PathVariable String id, @RequestParam String emoji) {
        return ResponseEntity.ok(messageService.toggleReaction(id, emoji));
    }

    @PostMapping("/conversation/{conversationId}/poll")
    public ResponseEntity<MessageDTO> createPoll(
            @PathVariable String conversationId,
            @RequestBody PollCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.createPoll(
                        conversationId,
                        request.getUserId(),
                        request.getQuestion(),
                        request.getOptions(),
                        request.isMultipleChoice()
                ));
    }

    @PostMapping("/{id}/poll-vote")
    public ResponseEntity<MessageDTO> votePoll(@PathVariable String id, @RequestBody PollVoteRequest request) {
        return ResponseEntity.ok(messageService.votePoll(id, request.getUserId(), request.getOptionIds()));
    }
}

