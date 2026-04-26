package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.FriendInviteDTO;
import edu.iuh.fit.se.commonservice.dto.GroupDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final AuthServiceClient authServiceClient;

    @GetMapping
    public ResponseEntity<List<GroupDTO>> getAllGroups() {
        return ResponseEntity.ok(groupService.getAllGroups());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO> getGroupById(@PathVariable String id) {
        return ResponseEntity.ok(groupService.getGroupById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<GroupDTO>> searchGroups(@RequestParam String name) {
        return ResponseEntity.ok(groupService.searchGroups(name));
    }

    @GetMapping("/admin/{adminId}")
    public ResponseEntity<List<GroupDTO>> getGroupsByAdminId(@PathVariable String adminId) {
        return ResponseEntity.ok(groupService.getGroupsByAdminId(adminId));
    }

    @PostMapping
    public ResponseEntity<GroupDTO> createGroup(@RequestBody GroupDTO groupDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(groupDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDTO> updateGroup(@PathVariable String id, @RequestBody GroupDTO groupDTO) {
        return ResponseEntity.ok(groupService.updateGroup(id, groupDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
        groupService.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{groupId}/members")
    public void addMembers(
            @PathVariable String groupId,
            @RequestBody List<String> userIds
    ) {
        groupService.addMembers(groupId, userIds);
    }
    @GetMapping("/{groupId}/members")
    public List<UserDTO> getMembers(@PathVariable String groupId) {
        return groupService.getGroupMembers(groupId);
    }
    @DeleteMapping("/{groupId}/members/{userId}")
    public void removeMember(
            @PathVariable String groupId,
            @PathVariable String userId
    ) {
        groupService.removeMember(groupId, userId);
    }
    @GetMapping("/{groupId}/is-member/{userId}")
    public boolean isUserMember(
            @PathVariable String groupId,
            @PathVariable String userId) {

        return groupService.isUserMember(groupId, userId);

    }
    @PostMapping("/{groupId}/join/{userId}")
    public ResponseEntity<Void> joinGroup(
            @PathVariable String groupId,
            @PathVariable String userId) {

        groupService.joinGroup(groupId, userId);

        return ResponseEntity.ok().build();
    }
    @GetMapping("/{groupId}/role/{userId}")
    public ResponseEntity<String> getUserRole(
            @PathVariable String groupId,
            @PathVariable String userId) {

        String role = groupService.getUserRole(groupId, userId);

        return ResponseEntity.ok(role);
    }
    @DeleteMapping("/{groupId}/leave/{userId}")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable String groupId,
            @PathVariable String userId) {

        groupService.leaveGroup(groupId, userId);

        return ResponseEntity.ok().build();
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<GroupDTO>> getGroupsByUserId(
            @PathVariable String userId) {

        return ResponseEntity.ok(groupService.getGroupsByUserId(userId));
    }

    @GetMapping("/{groupId}/invitable-friends/{userId}")
    public ResponseEntity<List<FriendInviteDTO>> getInvitableFriends(
            @PathVariable String groupId,
            @PathVariable String userId) {

        return ResponseEntity.ok(
                groupService.getInvitableFriends(groupId, userId)
        );
    }

    @GetMapping("/{groupId}/status/{userId}")
    public ResponseEntity<String> getUserStatus(
            @PathVariable String groupId,
            @PathVariable String userId
    ) {
        String status = groupService.getUserStatus(groupId, userId);
        return ResponseEntity.ok(status);
    }
    public record PendingMemberDTO(String userId, String fullName, String avatar, String status) {}
    @GetMapping("/{groupId}/pending-members")
    public ResponseEntity<List<PendingMemberDTO>> getPendingMembers(@PathVariable String groupId) {
        List<edu.iuh.fit.se.commonservice.model.GroupMember> pending = groupService.getPendingMembers(groupId);
        List<String> userIds = pending.stream().map(edu.iuh.fit.se.commonservice.model.GroupMember::getUserId).toList();
        
        java.util.Map<String, UserDTO> usersMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                List<UserDTO> users = authServiceClient.batchLookup(userIds);
                if (users != null) {
                    for (UserDTO u : users) {
                        usersMap.put(u.getId(), u);
                    }
                }
            } catch (Exception e) {}
        }
        
        List<PendingMemberDTO> dtos = pending.stream()
                .map(m -> {
                    UserDTO u = usersMap.get(m.getUserId());
                    return new PendingMemberDTO(
                        m.getUserId(),
                        u != null ? (u.getFullName() != null ? u.getFullName() : u.getUsername()) : "Unknown",
                        u != null ? u.getAvatar() : "",
                        m.getStatus()
                    );
                })
                .toList();

        return ResponseEntity.ok(dtos);
    }
    /**
     * Toggle trạng thái privacy của group:
     * PUBLIC <-> PRIVATE
     * Nếu chuyển PRIVATE -> PUBLIC, các member PENDING sẽ thành ACTIVE
     */
    @PostMapping("/{groupId}/toggle-privacy")
    public ResponseEntity<GroupDTO> toggleGroupPrivacy(@PathVariable String groupId) {
        GroupDTO updatedGroup = groupService.toggleGroupPrivacy(groupId);
        return ResponseEntity.ok(updatedGroup);
    }
    @PostMapping("/{groupId}/members/{userId}/approve")
    public ResponseEntity<Void> approveMember(
            @PathVariable String groupId,
            @PathVariable String userId) {

        groupService.approveMember(groupId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupId}/members/{userId}/reject")
    public ResponseEntity<Void> rejectMember(
            @PathVariable String groupId,
            @PathVariable String userId) {

        groupService.rejectMember(groupId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{groupId}/invite/{userId}/reject")
    public ResponseEntity<Void> rejectGroupInvite(
            @PathVariable String groupId,
            @PathVariable String userId) {

        groupService.rejectGroupInvite(groupId, userId);
        return ResponseEntity.ok().build();
    }
}

