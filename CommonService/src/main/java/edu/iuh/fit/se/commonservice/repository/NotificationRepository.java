package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId);
    long countByRecipientIdAndIsReadFalse(String recipientId);
    void deleteByRecipientId(String recipientId);
}

