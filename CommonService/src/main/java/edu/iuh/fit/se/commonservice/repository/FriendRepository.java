package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Friend;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRepository extends MongoRepository<Friend, String> {
    List<Friend> findByUserId(String userId);
    List<Friend> findByFriendId(String friendId);
    /** Single query to find both directions of friendship */
    List<Friend> findByUserIdOrFriendId(String userId, String friendId);
    Optional<Friend> findByUserIdAndFriendId(String userId, String friendId);
    boolean existsByUserIdAndFriendId(String userId, String friendId);
}

