package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.dto.ConversationMetaUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.ConversationPinRequest;
import edu.iuh.fit.se.messegeservice.dto.ConversationVisibilityRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupMemberUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupRoleUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.LeaveGroupRequest;
import edu.iuh.fit.se.messegeservice.dto.JoinRequestUpdateRequest;
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

    @GetMapping("/user/{userId}/hidden")
    public ResponseEntity<List<ConversationDTO>> getHiddenConversationsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(conversationService.getHiddenConversationsByUserId(userId));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<ConversationDTO>> getGroupConversations() {
        return ResponseEntity.ok(conversationService.getGroupConversations());
    }

    @GetMapping("/groups/search")
    public ResponseEntity<List<ConversationDTO>> searchGroupConversations(
            @RequestParam String userId,
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(conversationService.searchGroupConversations(userId, keyword));
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

    @PostMapping("/{id}/hide")
    public ResponseEntity<ConversationDTO> hideConversation(@PathVariable String id, @RequestBody ConversationPinRequest request) {
        return ResponseEntity.ok(conversationService.hideConversation(id, request));
    }

    @PostMapping("/{id}/unhide")
    public ResponseEntity<ConversationDTO> unhideConversation(@PathVariable String id, @RequestBody ConversationPinRequest request) {
        return ResponseEntity.ok(conversationService.unhideConversation(id, request));
    }

    @PostMapping("/{id}/clear-for-user")
    public ResponseEntity<ConversationDTO> clearConversationForUser(
            @PathVariable String id,
            @RequestBody ConversationVisibilityRequest request
    ) {
        return ResponseEntity.ok(conversationService.clearConversationForUser(id, request.getUserId()));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ConversationDTO> restoreConversation(
            @PathVariable String id,
            @RequestBody ConversationVisibilityRequest request
    ) {
        return ResponseEntity.ok(conversationService.restoreConversation(id, request.getUserId()));
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

    /**
     * Update group name/avatar with permission checks (owner/admin for groups).
     */
    @PutMapping("/{id}/meta")
    public ResponseEntity<ConversationDTO> updateConversationMeta(@PathVariable String id, @RequestBody ConversationMetaUpdateRequest request) {
        return ResponseEntity.ok(conversationService.updateConversationMeta(id, request));
    }

    /**
     * Leave a group. If requester is owner, must transfer ownership first (newOwnerId).
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<ConversationDTO> leaveGroup(@PathVariable String id, @RequestBody LeaveGroupRequest request) {
        return ResponseEntity.ok(conversationService.leaveGroup(id, request));
    }

    @PostMapping("/{id}/disband")
    public ResponseEntity<ConversationDTO> disbandGroup(@PathVariable String id, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.disbandGroup(id, requesterId));
    }

    @PostMapping("/{id}/join-requests")
    public ResponseEntity<ConversationDTO> requestToJoin(@PathVariable String id, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.requestToJoin(id, requesterId));
    }

    @GetMapping("/{id}/join-requests")
    public ResponseEntity<List<String>> getPendingJoinRequests(@PathVariable String id, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.getPendingJoinRequests(id, requesterId));
    }

    @PutMapping("/{id}/join-requests")
    public ResponseEntity<ConversationDTO> handleJoinRequest(@PathVariable String id, @RequestBody JoinRequestUpdateRequest request) {
        return ResponseEntity.ok(conversationService.handleJoinRequest(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id, @RequestParam(required = false) String requesterId) {
        conversationService.deleteConversation(id, requesterId);
        return ResponseEntity.noContent().build();
    }

    // ── Phase A Endpoints ───────────────────────────────────────────────────

    @PostMapping("/{id}/mute")
    public ResponseEntity<ConversationDTO> toggleMute(@PathVariable String id, @RequestBody edu.iuh.fit.se.messegeservice.dto.ConversationActionRequest request) {
        return ResponseEntity.ok(conversationService.toggleMute(id, request.getUserId()));
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<ConversationDTO> togglePin(@PathVariable String id, @RequestBody edu.iuh.fit.se.messegeservice.dto.ConversationActionRequest request) {
        return ResponseEntity.ok(conversationService.togglePin(id, request.getUserId()));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<ConversationDTO> toggleBan(@PathVariable String id, @RequestBody edu.iuh.fit.se.messegeservice.dto.ConversationActionRequest request) {
        return ResponseEntity.ok(conversationService.toggleBan(id, request.getUserId(), request.getPayload())); // Payload is the target userId
    }

    @PostMapping("/{id}/nickname")
    public ResponseEntity<ConversationDTO> updateNickname(@PathVariable String id, @RequestBody edu.iuh.fit.se.messegeservice.dto.ConversationActionRequest request) {
        // Payload is the nickname, userId is the target user
        return ResponseEntity.ok(conversationService.updateNickname(id, request.getUserId(), request.getPayload()));
    }

    // ── Phase B: Invite Links ───────────────────────────────────────────────

    @GetMapping("/{id}/invite-link")
    public ResponseEntity<String> getInviteLink(@PathVariable String id, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.getInviteLink(id, requesterId));
    }

    @PostMapping("/{id}/invite-link/reset")
    public ResponseEntity<String> resetInviteLink(@PathVariable String id, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.resetInviteLink(id, requesterId));
    }

    @PostMapping("/join-by-url")
    public ResponseEntity<ConversationDTO> joinByInviteLink(@RequestParam String token, @RequestParam String requesterId) {
        return ResponseEntity.ok(conversationService.joinByInviteLink(token, requesterId));
    }

    // ── Phase C: Advanced ───────────────────────────────────────────────────

    @PostMapping("/{id}/block")
    public ResponseEntity<ConversationDTO> toggleBlock(@PathVariable String id, @RequestParam String userId) {
        return ResponseEntity.ok(conversationService.toggleBlock(id, userId));
    }

    @PostMapping("/{id}/background")
    public ResponseEntity<ConversationDTO> updateBackground(@PathVariable String id, @RequestParam String backgroundUrl) {
        return ResponseEntity.ok(conversationService.updateBackground(id, backgroundUrl));
    }
}

