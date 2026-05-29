package edu.iuh.fit.se.mediaservice.repository;

import edu.iuh.fit.se.mediaservice.model.PaymentTransaction;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends MongoRepository<PaymentTransaction, String> {
    Optional<PaymentTransaction> findByOrderCode(String orderCode);
    List<PaymentTransaction> findByUserId(String userId, Sort sort);
    List<PaymentTransaction> findByUserIdAndStatus(String userId, String status, Sort sort);
    List<PaymentTransaction> findByStatus(String status);
    long countByStatus(String status);
    List<PaymentTransaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
