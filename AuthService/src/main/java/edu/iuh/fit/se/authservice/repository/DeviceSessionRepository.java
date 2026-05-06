package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.DeviceSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository extends JpaRepository<DeviceSessionEntity, String> {

    List<DeviceSessionEntity> findByUserIdOrderByLastSeenAtDesc(String userId);

    Optional<DeviceSessionEntity> findByRefreshToken(String refreshToken);

    List<DeviceSessionEntity> findByUserIdAndRevokedFalse(String userId);
}
