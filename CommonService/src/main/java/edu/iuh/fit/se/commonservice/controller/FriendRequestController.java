package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.FriendRequestDTO;
import edu.iuh.fit.se.commonservice.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @GetMapping("/sender/{senderId}")
    public ResponseEntity<List<FriendRequestDTO>> getFriendRequestsBySenderId(@PathVariable String senderId) {
        return ResponseEntity.ok(friendRequestService.getFriendRequestsBySenderId(senderId));
    }

    @GetMapping("/receiver/{receiverId}")
    public ResponseEntity<List<FriendRequestDTO>> getFriendRequestsByReceiverId(@PathVariable String receiverId) {
        return ResponseEntity.ok(friendRequestService.getFriendRequestsByReceiverId(receiverId));
    }

    @GetMapping("/receiver/{receiverId}/pending")
    public ResponseEntity<List<FriendRequestDTO>> getPendingFriendRequestsByReceiverId(@PathVariable String receiverId) {
        return ResponseEntity.ok(friendRequestService.getPendingFriendRequestsByReceiverId(receiverId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FriendRequestDTO> getFriendRequestById(@PathVariable String id) {
        return ResponseEntity.ok(friendRequestService.getFriendRequestById(id));
    }

    @PostMapping
    public ResponseEntity<FriendRequestDTO> createFriendRequest(@RequestBody FriendRequestDTO friendRequestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendRequestService.createFriendRequest(friendRequestDTO));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<FriendRequestDTO> acceptFriendRequest(@PathVariable String id) {
        return ResponseEntity.ok(friendRequestService.acceptFriendRequest(id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectFriendRequest(@PathVariable String id) {
        friendRequestService.rejectFriendRequest(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelFriendRequest(@PathVariable String id) {
        friendRequestService.cancelFriendRequest(id);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/unfriend")
    public ResponseEntity<Void> unfriend(@RequestParam String userId1, @RequestParam String userId2) {
        friendRequestService.unfriend(userId1, userId2);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFriendRequest(@PathVariable String id) {
        friendRequestService.deleteFriendRequest(id);
        return ResponseEntity.noContent().build();
    }
}

