package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendSuggestionDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.FriendRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendSuggestionService {

    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserIdentityService userIdentityService;

    /**
     * Returns up to {@code limit} friend suggestions for the given user,
     * ranked by number of mutual friends (friends-of-friends algorithm).
     * Performance: single-hop BFS, O(F²) where F = friend count — acceptable for social graphs up to ~1000 friends.
     */
    public List<FriendSuggestionDTO> getSuggestions(String userId, int limit) {
        // 1. Collect direct friend IDs
        Set<String> directFriendIds = getDirectFriendIds(userId);

        // 2. Build excluded set (self + existing friends + already-requested)
        Set<String> excluded = new HashSet<>(directFriendIds);
        excluded.add(userId);

        // Exclude users who already have a pending request in either direction
        friendRequestRepository.findBySenderId(userId).stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .forEach(r -> excluded.add(r.getReceiverId()));
        friendRequestRepository.findByReceiverId(userId).stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .forEach(r -> excluded.add(r.getSenderId()));

        // 3. Count mutual friends for 2nd-degree connections
        Map<String, Integer> mutualCount = new HashMap<>();
        for (String friendId : directFriendIds) {
            Set<String> friendsOfFriend = getDirectFriendIds(friendId);
            for (String candidate : friendsOfFriend) {
                if (!excluded.contains(candidate)) {
                    mutualCount.merge(candidate, 1, Integer::sum);
                }
            }
        }

        if (mutualCount.isEmpty()) {
            return List.of();
        }

        // 4. Batch-fetch user profiles for candidates
        List<String> candidateIds = mutualCount.keySet().stream()
                .sorted(Comparator.comparingInt(mutualCount::get).reversed())
                .limit(Math.min(limit * 3L, 60))  // fetch a bit more than needed, cap at 60
                .collect(Collectors.toList());

        Map<String, UserDTO> profiles = userIdentityService.batchLookupMap(candidateIds);

        // 5. Build result list, sorted by mutual count desc
        List<FriendSuggestionDTO> suggestions = new ArrayList<>();
        for (String candidateId : candidateIds) {
            UserDTO profile = profiles.get(candidateId);
            if (profile == null || Boolean.FALSE.equals(profile.getIsActive())) continue;
            suggestions.add(new FriendSuggestionDTO(
                    candidateId,
                    profile.getFullName() != null ? profile.getFullName() : profile.getUsername(),
                    profile.getUsername(),
                    profile.getAvatar(),
                    mutualCount.getOrDefault(candidateId, 0)
            ));
        }

        suggestions.sort(Comparator.comparingInt(FriendSuggestionDTO::getMutualFriendCount).reversed());
        return suggestions.stream().limit(limit).collect(Collectors.toList());
    }

    /** Collect all friend IDs for a user (both directions of the friendship edge). */
    private Set<String> getDirectFriendIds(String userId) {
        Set<String> ids = new HashSet<>();
        friendRepository.findByUserId(userId).stream().map(Friend::getFriendId).forEach(ids::add);
        friendRepository.findByFriendId(userId).stream().map(Friend::getUserId).forEach(ids::add);
        return ids;
    }
}
