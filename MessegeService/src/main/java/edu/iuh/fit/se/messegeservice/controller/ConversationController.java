package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.dto.GroupMemberUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupRoleUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.RemoveMemberRequest;
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

    @PostMapping("/group")
    public ResponseEntity<ConversationDTO> createGroupConversation(@RequestBody ConversationDTO conversationDTO) {
        conversationDTO.setGroup(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.createConversation(conversationDTO));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ConversationDTO> addMembers(@PathVariable String id, @RequestBody GroupMemberUpdateRequest request) {
        return ResponseEntity.ok(conversationService.addMembers(id, request));
    }

    @DeleteMapping("/{id}/members")
    public ResponseEntity<ConversationDTO> removeMember(@PathVariable String id, @RequestBody RemoveMemberRequest request) {
        return ResponseEntity.ok(conversationService.removeMember(id, request));
    }

    @PatchMapping("/{id}/roles")
    public ResponseEntity<ConversationDTO> updateRoles(@PathVariable String id, @RequestBody GroupRoleUpdateRequest request) {
        return ResponseEntity.ok(conversationService.updateGroupRoles(id, request));
    }

    // Accept PUT for clients that send full replacements instead of partial updates
    @PutMapping("/{id}/roles")
    public ResponseEntity<ConversationDTO> upsertRoles(@PathVariable String id, @RequestBody GroupRoleUpdateRequest request) {
        return ResponseEntity.ok(conversationService.updateGroupRoles(id, request));
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

