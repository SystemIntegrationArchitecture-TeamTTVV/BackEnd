package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<ConversationDTO>> getConversationsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(conversationService.getConversationsByUserId(userId));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<ConversationDTO>> getGroupConversations() {
        return ResponseEntity.ok(conversationService.getGroupConversations());
    }

    @GetMapping("/direct")
    public ResponseEntity<List<ConversationDTO>> getDirectConversations() {
        return ResponseEntity.ok(conversationService.getDirectConversations());
    }

    @GetMapping("/direct/{userId1}/{userId2}")
    public ResponseEntity<ConversationDTO> getOrCreateDirectConversation(
            @PathVariable String userId1, 
            @PathVariable String userId2) {
        return ResponseEntity.ok(conversationService.getOrCreateDirectConversation(userId1, userId2));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDTO> getConversationById(@PathVariable String id) {
        return ResponseEntity.ok(conversationService.getConversationById(id));
    }

    @PostMapping
    public ResponseEntity<ConversationDTO> createConversation(@RequestBody ConversationDTO conversationDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.createConversation(conversationDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationDTO> updateConversation(@PathVariable String id, @RequestBody ConversationDTO conversationDTO) {
        return ResponseEntity.ok(conversationService.updateConversation(id, conversationDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }
}

