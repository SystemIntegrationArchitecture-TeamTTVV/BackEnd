package edu.iuh.fit.se.mediaservice.repository;

import edu.iuh.fit.se.mediaservice.model.WalletEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletEventRepository extends MongoRepository<WalletEvent, String> {
    List<WalletEvent> findByUserIdOrderByCreatedAtDesc(String userId);
    List<WalletEvent> findByWalletIdOrderByCreatedAtDesc(String walletId);
}
