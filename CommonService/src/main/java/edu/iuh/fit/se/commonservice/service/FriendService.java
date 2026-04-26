package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendDTO;
import edu.iuh.fit.se.commonservice.dto.UserDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final edu.iuh.fit.se.commonservice.repository.FriendRequestRepository friendRequestRepository;
    private final UserIdentityService userIdentityService;

    public List<FriendDTO> getFriendsByUserId(String userId) {
        return friendRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public FriendDTO addFriend(String userId, String friendId) {
        if (friendRepository.existsByUserIdAndFriendId(userId, friendId)) {
            throw new RuntimeException("Already friends");
        }

        userIdentityService.getByIdOrThrow(userId);
        userIdentityService.getByIdOrThrow(friendId);

        Friend friendEntity = new Friend();
        friendEntity.setUserId(userId);
        friendEntity.setFriendId(friendId);
        friendEntity.setCreatedAt(LocalDateTime.now());
        friendEntity.setUpdatedAt(LocalDateTime.now());

        Friend saved = friendRepository.save(friendEntity);
        return toDTO(saved);
    }

    public void removeFriend(String userId, String friendId) {
        Friend friend = friendRepository.findByUserIdAndFriendId(userId, friendId)
                .orElseThrow(() -> new RuntimeException("Friendship not found"));
        friendRepository.delete(friend);
    }

    public boolean checkIfFriends(String userId, String friendId) {
        return friendRepository.existsByUserIdAndFriendId(userId, friendId)
                || friendRepository.existsByUserIdAndFriendId(friendId, userId);
    }

    public String getFriendStatus(String userId, String targetUserId) {
        if (checkIfFriends(userId, targetUserId)) {
            return "ACCEPTED";
        }
        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(userId, targetUserId, "PENDING")) {
            return "REQUEST_SENT";
        }
        if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatus(targetUserId, userId, "PENDING")) {
            return "REQUEST_RECEIVED";
        }
        return "NONE";
    }

    public List<FriendDTO> getMutualFriends(String userId1, String userId2) {
        List<Friend> friends1 = friendRepository.findByUserId(userId1);
        List<Friend> friends2 = friendRepository.findByUserId(userId2);

        List<String> friendIds1 = friends1.stream().map(Friend::getFriendId).collect(Collectors.toList());
        List<String> friendIds2 = friends2.stream().map(Friend::getFriendId).collect(Collectors.toList());

        List<String> mutualFriendIds = friendIds1.stream()
                .filter(friendIds2::contains)
                .collect(Collectors.toList());

        return mutualFriendIds.stream()
                .map(friendId -> {
                    Friend friend = friendRepository.findByUserIdAndFriendId(userId1, friendId).orElse(null);
                    return friend != null ? toDTO(friend) : null;
                })
                .filter(friendDTO -> friendDTO != null)
                .collect(Collectors.toList());
    }

    private FriendDTO toDTO(Friend friend) {
        FriendDTO dto = new FriendDTO();
        dto.setId(friend.getId());
        dto.setUserId(friend.getUserId());
        dto.setFriendId(friend.getFriendId());
        dto.setCreatedAt(friend.getCreatedAt());
        dto.setUpdatedAt(friend.getUpdatedAt());

        Map<String, UserDTO> users = userIdentityService.batchLookupMap(
                List.of(friend.getUserId(), friend.getFriendId()));
        UserDTO u = users.get(friend.getUserId());
        if (u != null) {
            dto.setUserName(u.getFullName());
            dto.setUserAvatar(u.getAvatar());
        }
        UserDTO f = users.get(friend.getFriendId());
        if (f != null) {
            dto.setFriendName(f.getFullName());
            dto.setFriendAvatar(f.getAvatar());
        }
        return dto;
    }
}
