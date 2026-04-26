package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/common/privacy")
@RequiredArgsConstructor
public class PrivacyCheckController {

    private final UserIdentityService userIdentityService;
    private final FriendRepository friendRepository;

    @GetMapping("/can-message")
    public ResponseEntity<Map<String, Object>> canMessage(
            @RequestParam String senderId,
            @RequestParam String receiverId
    ) {
        return checkPrivacy(senderId, receiverId, "allowMessageFrom", "nhắn tin");
    }

    @GetMapping("/can-call")
    public ResponseEntity<Map<String, Object>> canCall(
            @RequestParam String callerId,
            @RequestParam String receiverId
    ) {
        return checkPrivacy(callerId, receiverId, "allowCallFrom", "gọi điện");
    }

    @GetMapping("/can-invite-group")
    public ResponseEntity<Map<String, Object>> canInviteGroup(
            @RequestParam String inviterId,
            @RequestParam String targetUserId
    ) {
        return checkPrivacy(inviterId, targetUserId, "allowGroupInviteFrom", "mời vào nhóm");
    }

    private ResponseEntity<Map<String, Object>> checkPrivacy(
            String actorId, String targetId, String settingField, String actionLabel
    ) {
        if (actorId == null || targetId == null || actorId.equals(targetId)) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }

        UserDTO target = userIdentityService.findById(targetId).orElse(null);
        if (target == null) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }

        String setting = getPrivacySetting(target, settingField);
        if (!"FRIENDS_ONLY".equals(setting)) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }

        boolean friends = friendRepository.areFriends(actorId, targetId);
        if (friends) {
            return ResponseEntity.ok(Map.of("allowed", true));
        }

        String reason = "Người dùng này chỉ cho phép bạn bè " + actionLabel;
        log.info("Privacy blocked: {} cannot {} {} (FRIENDS_ONLY)", actorId, actionLabel, targetId);
        return ResponseEntity.ok(Map.of("allowed", false, "reason", reason));
    }

    private String getPrivacySetting(UserDTO user, String field) {
        return switch (field) {
            case "allowMessageFrom" -> user.getAllowMessageFrom() != null ? user.getAllowMessageFrom() : "EVERYONE";
            case "allowCallFrom" -> user.getAllowCallFrom() != null ? user.getAllowCallFrom() : "EVERYONE";
            case "allowGroupInviteFrom" -> user.getAllowGroupInviteFrom() != null ? user.getAllowGroupInviteFrom() : "EVERYONE";
            default -> "EVERYONE";
        };
    }
}
