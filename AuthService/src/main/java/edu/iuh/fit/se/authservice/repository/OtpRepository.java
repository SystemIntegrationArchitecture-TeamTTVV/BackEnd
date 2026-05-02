package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpEntity, String> {
    Optional<OtpEntity> findByEmailAndCode(String email, String code);
    void deleteByEmail(String email);
}
