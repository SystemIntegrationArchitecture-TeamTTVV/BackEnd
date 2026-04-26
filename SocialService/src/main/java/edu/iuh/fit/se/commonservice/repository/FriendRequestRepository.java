package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.FriendRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository extends MongoRepository<FriendRequest, String> {
    List<FriendRequest> findBySenderId(String senderId);
    List<FriendRequest> findByReceiverId(String receiverId);
    List<FriendRequest> findByReceiverIdAndStatus(String receiverId, String status);
    Optional<FriendRequest> findBySenderIdAndReceiverId(String senderId, String receiverId);
    boolean existsBySenderIdAndReceiverId(String senderId, String receiverId);
    Optional<FriendRequest> findBySenderIdAndReceiverIdAndStatus(String senderId, String receiverId, String status);
    boolean existsBySenderIdAndReceiverIdAndStatus(String senderId, String receiverId, String status);
}

