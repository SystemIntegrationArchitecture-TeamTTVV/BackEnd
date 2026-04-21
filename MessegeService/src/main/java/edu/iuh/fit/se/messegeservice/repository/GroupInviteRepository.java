package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.GroupInvite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupInviteRepository extends MongoRepository<GroupInvite, String> {
    List<GroupInvite> findByInviteeIdAndStatus(String inviteeId, String status);
    List<GroupInvite> findByConversationIdAndStatus(String conversationId, String status);
    boolean existsByConversationIdAndInviteeIdAndStatus(String conversationId, String inviteeId, String status);
    Optional<GroupInvite> findByConversationIdAndInviteeId(String conversationId, String inviteeId);
}
