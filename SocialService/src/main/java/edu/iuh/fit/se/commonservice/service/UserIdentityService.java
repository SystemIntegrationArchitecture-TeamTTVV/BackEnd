package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.client.AuthServiceClient;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityService {

    private final AuthServiceClient authServiceClient;
    private final MongoTemplate mongoTemplate;

    public Optional<UserDTO> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(authServiceClient.getUserById(id));
        } catch (FeignException.NotFound e) {
            log.debug("User {} not found in AuthService, trying MongoDB fallback", id);
        } catch (FeignException e) {
            log.warn("AuthService getUserById failed for {}: {}", id, e.getMessage());
        }
        // Fallback: query MongoDB 'users' collection for legacy ObjectId-based users
        return findInMongoFallback(id);
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
        List<String> distinctIds = ids.stream().distinct().toList();
        List<UserDTO> list = batchLookup(distinctIds);
        Map<String, UserDTO> result = new HashMap<>(
                list.stream().collect(Collectors.toMap(UserDTO::getId, Function.identity(), (a, b) -> a))
        );

        // Fallback: for any IDs not found in AuthService, try MongoDB 'users' collection
        Set<String> foundIds = result.keySet();
        List<String> missingIds = distinctIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            log.debug("Falling back to MongoDB for {} missing user IDs", missingIds.size());
            for (String missingId : missingIds) {
                findInMongoFallback(missingId).ifPresent(dto -> result.put(dto.getId(), dto));
            }
        }
        return result;
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

    /**
     * Fallback: look up user in MongoDB's 'users' collection (legacy data from
     * when the app was a monolith). Tries both String id and ObjectId.
     */
    private Optional<UserDTO> findInMongoFallback(String id) {
        try {
            Document doc = null;

            // Try by string _id first
            doc = mongoTemplate.findById(id, Document.class, "users");

            // Try by ObjectId if string lookup failed
            if (doc == null && ObjectId.isValid(id)) {
                doc = mongoTemplate.findById(new ObjectId(id), Document.class, "users");
            }

            if (doc == null) {
                return Optional.empty();
            }

            UserDTO dto = new UserDTO();
            Object docId = doc.get("_id");
            dto.setId(docId instanceof ObjectId ? ((ObjectId) docId).toHexString() : docId.toString());
            dto.setUsername(doc.getString("username"));
            dto.setEmail(doc.getString("email"));

            // Try multiple common field names for the display name
            String fullName = doc.getString("fullName");
            if (fullName == null || fullName.isBlank()) {
                fullName = doc.getString("displayName");
            }
            if (fullName == null || fullName.isBlank()) {
                String first = doc.getString("firstName");
                String last = doc.getString("lastName");
                if (first != null || last != null) {
                    fullName = ((last != null ? last : "") + " " + (first != null ? first : "")).trim();
                }
            }
            if (fullName == null || fullName.isBlank()) {
                fullName = doc.getString("username");
            }
            dto.setFullName(fullName);
            dto.setFirstName(doc.getString("firstName"));
            dto.setLastName(doc.getString("lastName"));
            dto.setAvatar(doc.getString("avatar"));

            log.debug("Found legacy user in MongoDB: id={}, name={}", dto.getId(), dto.getFullName());
            return Optional.of(dto);
        } catch (Exception e) {
            log.debug("MongoDB user fallback failed for {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }
}

