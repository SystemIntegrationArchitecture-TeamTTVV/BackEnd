package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Call;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallRepository extends MongoRepository<Call, String> {
    List<Call> findByConversationIdOrderByStartedAtDesc(String conversationId);
    List<Call> findByCallerIdOrderByStartedAtDesc(String callerId);
}

