package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // ── Primary listing query ─────────────────────────────────────────────────
    // Uses $in which maps to the compound index {participantIds:1, lastMessageAt:-1}
    // much more efficiently than Spring's "Containing" derived query on Atlas.
    @Query("{ 'participantIds': { $in: [?0] }, 'isDisbanded': { $ne: true } }")
    List<Conversation> findByParticipantIdsContainingOrderByLastMessageAtDesc(String userId);

    // ── Excluding hidden conversations at DB level (avoids in-memory filter) ──
    @Query("{ 'participantIds': { $in: [?0] }, '_id': { $nin: ?1 }, 'isDisbanded': { $ne: true } }")
    List<Conversation> findVisibleConversations(String userId, Collection<String> hiddenIds, Pageable pageable);

    // ── Group / direct listing ─────────────────────────────────────────────────
    List<Conversation> findByIsGroupTrueOrderByLastMessageAtDesc();
    List<Conversation> findByIsGroupFalseOrderByLastMessageAtDesc();

    // ── Group search ─────────────────────────────────────────────────────────
    List<Conversation> findByParticipantIdsContainingAndIsGroupTrueAndGroupNameContainingIgnoreCaseOrderByLastMessageAtDesc(
            String userId,
            String keyword
    );

    Optional<Conversation> findByInviteLinkToken(String inviteLinkToken);
    long countByPinnedByUserIds(String userId);
}

