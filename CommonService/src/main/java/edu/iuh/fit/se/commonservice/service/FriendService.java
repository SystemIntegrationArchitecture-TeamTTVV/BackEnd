package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.dto.FriendDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.User;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    public List<FriendDTO> getFriendsByUserId(String userId) {
        return friendRepository.findByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public FriendDTO addFriend(String userId, String friendId) {
        if (friendRepository.existsByUserIdAndFriendId(userId, friendId)) {
            throw new RuntimeException("Already friends");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new RuntimeException("Friend not found"));
        
        Friend friendEntity = new Friend();
        friendEntity.setUser(user);
        friendEntity.setUserId(userId);
        friendEntity.setFriend(friend);
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

    private FriendDTO toDTO(Friend friend) {
        FriendDTO dto = new FriendDTO();
        dto.setId(friend.getId());
        dto.setUserId(friend.getUserId());
        if (friend.getUser() != null) {
            dto.setUserName(friend.getUser().getFullName());
            dto.setUserAvatar(friend.getUser().getAvatar());
        }
        dto.setFriendId(friend.getFriendId());
        if (friend.getFriend() != null) {
            dto.setFriendName(friend.getFriend().getFullName());
            dto.setFriendAvatar(friend.getFriend().getAvatar());
        }
        dto.setCreatedAt(friend.getCreatedAt());
        dto.setUpdatedAt(friend.getUpdatedAt());
        return dto;
    }
}

