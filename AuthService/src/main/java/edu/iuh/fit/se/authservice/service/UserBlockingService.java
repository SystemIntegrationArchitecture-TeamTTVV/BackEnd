package edu.iuh.fit.se.authservice.service;

import edu.iuh.fit.se.authservice.dto.BlockedUserDTO;
import edu.iuh.fit.se.authservice.entity.UserBlockEntity;
import edu.iuh.fit.se.authservice.entity.UserEntity;
import edu.iuh.fit.se.authservice.repository.UserBlockRepository;
import edu.iuh.fit.se.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBlockingService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Value("${app.features.blocking.enabled:false}")
    private boolean blockingEnabled;

    public boolean isEnabled() {
        return blockingEnabled;
    }

    private void ensureFeatureEnabled() {
        if (!blockingEnabled) {
            throw new IllegalStateException("Blocking feature is disabled");
        }
    }

    @Transactional
    public void blockUser(String blockerUserId, String blockedUserId) {
        ensureFeatureEnabled();
        if (blockerUserId == null || blockedUserId == null || blockerUserId.isBlank() || blockedUserId.isBlank()) {
            throw new RuntimeException("Invalid user ids");
        }
        if (blockerUserId.equals(blockedUserId)) {
            throw new RuntimeException("You cannot block yourself");
        }

        userRepository.findById(blockedUserId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (userBlockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)) {
            return;
        }

        UserBlockEntity block = UserBlockEntity.builder()
                .blockerUserId(blockerUserId)
                .blockedUserId(blockedUserId)
                .build();
        userBlockRepository.save(block);
    }

    @Transactional
    public void unblockUser(String blockerUserId, String blockedUserId) {
        ensureFeatureEnabled();
        userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserDTO> getMyBlockedUsers(String blockerUserId) {
        ensureFeatureEnabled();
        return userBlockRepository.findByBlockerUserIdOrderByCreatedAtDesc(blockerUserId)
                .stream()
                .map(block -> toBlockedUserDto(block.getBlockedUserId(), block.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherDirection(String userA, String userB) {
        ensureFeatureEnabled();
        return userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userA, userB)
                || userBlockRepository.existsByBlockedUserIdAndBlockerUserId(userA, userB);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherDirectionSafe(String userA, String userB) {
        if (!blockingEnabled) {
            return false;
        }
        if (userA == null || userB == null || userA.isBlank() || userB.isBlank()) {
            return false;
        }
        if (userA.equals(userB)) {
            return false;
        }
        return userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userA, userB)
                || userBlockRepository.existsByBlockedUserIdAndBlockerUserId(userA, userB);
    }

    private BlockedUserDTO toBlockedUserDto(String blockedUserId, java.time.LocalDateTime blockedAt) {
        UserEntity user = userRepository.findById(blockedUserId)
                .orElseGet(() -> UserEntity.builder().id(blockedUserId).username("unknown").fullName("Unknown user").build());

        return BlockedUserDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatar(user.getAvatar())
                .blockedAt(blockedAt)
                .build();
    }
}
