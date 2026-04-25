package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, String> {
    Optional<PasswordResetTokenEntity> findByToken(String token);

    void deleteByUserId(String userId);
}
