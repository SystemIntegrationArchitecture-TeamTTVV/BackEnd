package edu.iuh.fit.se.authservice.repository;

import edu.iuh.fit.se.authservice.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    long countByActiveTrue();

    long countByCreatedAtAfter(java.time.LocalDateTime createdAt);
    
    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    
    long countByActiveTrueAndCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    
    List<UserEntity> findAllByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    @EntityGraph(attributePaths = {"interests", "role"})
    Optional<UserEntity> findById(String id);

    @EntityGraph(attributePaths = {"interests", "role"})
    Optional<UserEntity> findByUsername(String username);

    @EntityGraph(attributePaths = {"interests", "role"})
    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = {"interests", "role"})
    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<UserEntity> findByFullNameContainingIgnoreCase(@Param("q") String q);

    @EntityGraph(attributePaths = {"interests", "role"})
    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<UserEntity> findByUsernameContainingIgnoreCase(@Param("q") String q);
}
