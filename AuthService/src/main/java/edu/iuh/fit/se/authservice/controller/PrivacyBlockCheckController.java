package edu.iuh.fit.se.authservice.controller;

import edu.iuh.fit.se.authservice.service.UserBlockingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users/privacy")
@RequiredArgsConstructor
public class PrivacyBlockCheckController {

    private final UserBlockingService userBlockingService;

    @GetMapping("/can-message")
    public ResponseEntity<Map<String, Object>> canMessage(
            @RequestParam String senderId,
            @RequestParam String receiverId
    ) {
        return canInteract(senderId, receiverId, "nhắn tin");
    }

    @GetMapping("/can-call")
    public ResponseEntity<Map<String, Object>> canCall(
            @RequestParam String callerId,
            @RequestParam String receiverId
    ) {
        return canInteract(callerId, receiverId, "gọi điện");
    }

    @GetMapping("/can-invite-group")
    public ResponseEntity<Map<String, Object>> canInviteGroup(
            @RequestParam String inviterId,
            @RequestParam String targetUserId
    ) {
        return canInteract(inviterId, targetUserId, "mời vào nhóm");
    }

    @GetMapping("/is-blocked")
    public ResponseEntity<Map<String, Object>> isBlocked(
            @RequestParam String userA,
            @RequestParam String userB
    ) {
        boolean blocked = userBlockingService.isBlockedEitherDirectionSafe(userA, userB);
        return ResponseEntity.ok(Map.of(
                "blocked", blocked,
                "blockingFeatureEnabled", userBlockingService.isEnabled()
        ));
    }

    private ResponseEntity<Map<String, Object>> canInteract(String actorId, String targetId, String actionLabel) {
        if (actorId == null || targetId == null || actorId.equals(targetId)) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }

        boolean blocked = userBlockingService.isBlockedEitherDirectionSafe(actorId, targetId);
        if (blocked) {
            return ResponseEntity.ok(Map.of(
                    "allowed", false,
                    "reason", "Tương tác bị chặn theo cài đặt quyền riêng tư",
                    "action", actionLabel
            ));
        }

        return ResponseEntity.ok(Map.of("allowed", true));
    }
}
