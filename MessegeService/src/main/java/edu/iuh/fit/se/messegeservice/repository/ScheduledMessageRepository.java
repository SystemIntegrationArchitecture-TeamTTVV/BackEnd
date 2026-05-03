package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.ScheduledMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledMessageRepository extends MongoRepository<ScheduledMessage, String> {
    List<ScheduledMessage> findByConversationIdAndSenderIdOrderByScheduledAtAsc(String conversationId, String senderId);
    List<ScheduledMessage> findByConversationIdAndSenderIdAndStatusOrderByScheduledAtAsc(String conversationId, String senderId, String status);
    List<ScheduledMessage> findByStatusAndScheduledAtBefore(String status, LocalDateTime before);
}
