package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    long countByActiveTrue();

    long countByCreatedAtAfter(java.time.LocalDateTime createdAt);
    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<UserEntity> findByFullNameContainingIgnoreCase(@Param("q") String q);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<UserEntity> findByUsernameContainingIgnoreCase(@Param("q") String q);
}
