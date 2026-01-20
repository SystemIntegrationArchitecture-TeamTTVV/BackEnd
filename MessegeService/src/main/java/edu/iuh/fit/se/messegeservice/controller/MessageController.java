package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
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
    public ResponseEntity<List<MessageDTO>> getMessagesByConversationId(@PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getMessagesByConversationId(conversationId));
    }

    @GetMapping("/sender/{senderId}")
    public ResponseEntity<List<MessageDTO>> getMessagesBySenderId(@PathVariable String senderId) {
        return ResponseEntity.ok(messageService.getMessagesBySenderId(senderId));
    }

    @GetMapping("/conversation/{conversationId}/count")
    public ResponseEntity<Long> getMessageCountByConversationId(@PathVariable String conversationId) {
        return ResponseEntity.ok(messageService.getMessageCountByConversationId(conversationId));
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
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        messageService.deleteMessage(id);
        return ResponseEntity.noContent().build();
    }

@PostMapping("/{id}/pin")
public ResponseEntity<MessageDTO> togglePin(@PathVariable String id) {
    return ResponseEntity.ok(messageService.togglePin(id));
}

@PostMapping("/{id}/star")
public ResponseEntity<MessageDTO> toggleStar(@PathVariable String id, @RequestParam String userId) {
    return ResponseEntity.ok(messageService.toggleStar(id, userId));
}

@PostMapping("/{id}/react")
public ResponseEntity<MessageDTO> toggleReaction(@PathVariable String id, @RequestParam String emoji) {
    return ResponseEntity.ok(messageService.toggleReaction(id, emoji));
}
}

