package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.GroupInviteDTO;
import edu.iuh.fit.se.messegeservice.service.GroupInviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/group-invites")
@RequiredArgsConstructor
public class GroupInviteController {

    private final GroupInviteService groupInviteService;

    @PostMapping
    public ResponseEntity<GroupInviteDTO> sendInvite(@RequestBody GroupInviteDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupInviteService.sendInvite(request));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<GroupInviteDTO>> getInvitesForUser(@PathVariable String userId) {
        return ResponseEntity.ok(groupInviteService.getInvitesForUser(userId));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<GroupInviteDTO> acceptInvite(@PathVariable String id, @RequestParam String userId) {
        return ResponseEntity.ok(groupInviteService.acceptInvite(id, userId));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<GroupInviteDTO> declineInvite(@PathVariable String id, @RequestParam String userId) {
        return ResponseEntity.ok(groupInviteService.declineInvite(id, userId));
    }
}
