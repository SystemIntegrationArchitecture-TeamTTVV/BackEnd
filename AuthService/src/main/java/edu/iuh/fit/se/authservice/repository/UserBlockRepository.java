package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.UserBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlockEntity, String> {

    List<UserBlockEntity> findByBlockerUserIdOrderByCreatedAtDesc(String blockerUserId);

    Optional<UserBlockEntity> findByBlockerUserIdAndBlockedUserId(String blockerUserId, String blockedUserId);

    boolean existsByBlockerUserIdAndBlockedUserId(String blockerUserId, String blockedUserId);

    boolean existsByBlockedUserIdAndBlockerUserId(String blockedUserId, String blockerUserId);
}
