package edu.iuh.fit.se.messegeservice.client;

import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "authService", url = "${auth.service.base-url:http://localhost:8083}")
public interface AuthServiceClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUserById(@PathVariable("id") String id);

    @GetMapping("/api/users/username/{username}")
    UserDTO getUserByUsername(@PathVariable("username") String username);

    @PostMapping("/api/users/batch-lookup")
    List<UserDTO> batchLookup(@RequestBody List<String> ids);

    @GetMapping("/api/users/metrics/summary")
    Map<String, Object> userMetricsSummary();
}
