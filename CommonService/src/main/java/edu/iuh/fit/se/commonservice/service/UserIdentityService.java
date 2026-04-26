package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityService {

    private final AuthServiceClient authServiceClient;

    public Optional<UserDTO> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(authServiceClient.getUserById(id));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            log.warn("AuthService getUserById failed for {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public UserDTO getByIdOrThrow(String id) {
        return findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public Optional<UserDTO> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(authServiceClient.getUserByUsername(username));
        } catch (FeignException e) {
            log.warn("AuthService getUserByUsername failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<UserDTO> batchLookup(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            return authServiceClient.batchLookup(ids);
        } catch (FeignException e) {
            log.warn("AuthService batchLookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, UserDTO> batchLookupMap(List<String> ids) {
        List<UserDTO> list = batchLookup(ids.stream().distinct().toList());
        return list.stream().collect(Collectors.toMap(UserDTO::getId, Function.identity(), (a, b) -> a));
    }

    public Map<String, Object> userMetricsSummary() {
        try {
            return authServiceClient.userMetricsSummary();
        } catch (FeignException e) {
            log.warn("AuthService metrics failed: {}", e.getMessage());
            return Map.of("totalUsers", 0L, "activeUsers", 0L, "newUsersThisMonth", 0L);
        }
    }

    public void deactivateUser(String userId) {
        try {
            authServiceClient.deactivateUser(userId);
        } catch (FeignException e) {
            log.warn("AuthService deactivateUser failed for {}: {}", userId, e.getMessage());
        }
    }

    public List<UserDTO> getAllUsers() {
        try {
            return authServiceClient.getAllUsers();
        } catch (FeignException e) {
            log.warn("AuthService getAllUsers failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
