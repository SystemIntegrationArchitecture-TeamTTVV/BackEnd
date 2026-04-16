package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.HiddenConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HiddenConversationRepository extends MongoRepository<HiddenConversation, String> {
    List<HiddenConversation> findByUserId(String userId);
    Optional<HiddenConversation> findByUserIdAndConversationId(String userId, String conversationId);
    List<HiddenConversation> findByUserIdAndHiddenTrue(String userId);
    boolean existsByUserIdAndConversationIdAndHiddenTrue(String userId, String conversationId);
    List<HiddenConversation> findByConversationIdAndHiddenTrueAndRequirePinUnlockFalse(String conversationId);
}
