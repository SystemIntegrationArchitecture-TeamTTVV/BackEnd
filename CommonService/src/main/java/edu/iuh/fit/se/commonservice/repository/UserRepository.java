package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<User> findByFullNameContainingIgnoreCase(String fullName);
    List<User> findByUsernameContainingIgnoreCase(String username);
}

