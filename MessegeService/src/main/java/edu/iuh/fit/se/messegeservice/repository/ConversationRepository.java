package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(String userId);
    List<Conversation> findByIsGroupTrueOrderByLastMessageAtDesc();
    List<Conversation> findByIsGroupFalseOrderByLastMessageAtDesc();
    List<Conversation> findByParticipantIdsContainingAndIsGroupTrueAndGroupNameContainingIgnoreCaseOrderByLastMessageAtDesc(
            String userId,
            String keyword
    );
    Optional<Conversation> findByInviteLinkToken(String inviteLinkToken);
}
