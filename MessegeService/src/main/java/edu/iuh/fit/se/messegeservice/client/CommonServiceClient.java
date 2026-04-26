package edu.iuh.fit.se.messegeservice.client;

import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OpenFeign client for calling CommonService from MessegeService.
 */
@FeignClient(
        name = "commonService",
        url = "${common.service.url:http://localhost:8081}"
)
public interface CommonServiceClient {

    @PostMapping("/api/socket/emit/user/{username}")
    void emitToUser(@PathVariable("username") String username, @RequestBody SocketEventDTO event);

    @PostMapping("/api/socket/emit/all")
    void emitToAll(@RequestBody SocketEventDTO event);

    @PostMapping("/api/socket/emit/topic/{topic}")
    void emitToTopic(@PathVariable("topic") String topic, @RequestBody SocketEventDTO event);

    @PostMapping("/api/socket/emit/room/{roomId}")
    void emitToRoom(@PathVariable("roomId") String roomId, @RequestBody SocketEventDTO event);

    // ── Privacy Check ───────────────────────────────────────────────────
    @GetMapping("/api/common/privacy/can-message")
    java.util.Map<String, Object> canMessage(@RequestParam("senderId") String senderId, @RequestParam("receiverId") String receiverId);

    @GetMapping("/api/common/privacy/can-call")
    java.util.Map<String, Object> canCall(@RequestParam("callerId") String callerId, @RequestParam("receiverId") String receiverId);

    @GetMapping("/api/common/privacy/can-invite-group")
    java.util.Map<String, Object> canInviteGroup(@RequestParam("inviterId") String inviterId, @RequestParam("targetUserId") String targetUserId);
}

