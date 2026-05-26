package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    List<Transaction> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Transaction> findByUserIdAndTypeOrderByCreatedAtDesc(String userId, String type);
    List<Transaction> findByReceiverIdAndTypeOrderByCreatedAtDesc(String receiverId, String type);
    List<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByType(String type);
}

