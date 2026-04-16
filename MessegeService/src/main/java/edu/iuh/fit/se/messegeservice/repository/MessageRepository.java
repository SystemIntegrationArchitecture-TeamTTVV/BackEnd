package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    List<Message> findBySenderIdOrderByCreatedAtDesc(String senderId);
    List<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(String conversationId, Pageable pageable);
    List<Message> findByConversationIdAndIsDeletedFalseAndCreatedAtBeforeOrderByCreatedAtDesc(
            String conversationId,
            LocalDateTime before,
            Pageable pageable
    );
    List<Message> findByConversationIdAndIsDeletedFalseAndPinnedTrueOrderByCreatedAtDesc(String conversationId);
    List<Message> findByConversationIdAndIsDeletedFalseAndAttachmentsIsNotNullOrderByCreatedAtDesc(String conversationId);
    long countByConversationId(String conversationId);
}

