package edu.iuh.fit.se.messegeservice.client;

import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for calling CommonService from MessegeService.
 */
@FeignClient(
        name = "common-service",
        url = "${common.service.url:http://localhost:8081}"
)
public interface CommonServiceClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUserById(@PathVariable("id") String id);

    @PostMapping("/api/socket/emit/user/{username}")
    void emitToUser(@PathVariable("username") String username, @RequestBody SocketEventDTO event);

    @PostMapping("/api/socket/emit/all")
    void emitToAll(@RequestBody SocketEventDTO event);

    @PostMapping("/api/socket/emit/topic/{topic}")
    void emitToTopic(@PathVariable("topic") String topic, @RequestBody SocketEventDTO event);
}

