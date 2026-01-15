package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.FriendDTO;
import edu.iuh.fit.se.commonservice.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FriendDTO>> getFriendsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(friendService.getFriendsByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<FriendDTO> addFriend(@RequestParam String userId, @RequestParam String friendId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.addFriend(userId, friendId));
    }

    @DeleteMapping
    public ResponseEntity<Void> removeFriend(@RequestParam String userId, @RequestParam String friendId) {
        friendService.removeFriend(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> checkIfFriends(@RequestParam String userId, @RequestParam String friendId) {
        return ResponseEntity.ok(friendService.checkIfFriends(userId, friendId));
    }

    @GetMapping("/mutual")
    public ResponseEntity<List<FriendDTO>> getMutualFriends(@RequestParam String userId1, @RequestParam String userId2) {
        return ResponseEntity.ok(friendService.getMutualFriends(userId1, userId2));
    }
}

