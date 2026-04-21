package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Reminder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderRepository extends MongoRepository<Reminder, String> {
    List<Reminder> findByConversationId(String conversationId);
    List<Reminder> findByIsTriggeredFalseAndRemindAtBefore(LocalDateTime now);
}
