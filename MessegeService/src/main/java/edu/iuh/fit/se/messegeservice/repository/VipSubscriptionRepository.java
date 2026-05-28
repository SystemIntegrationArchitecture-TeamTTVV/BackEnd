package edu.iuh.fit.se.messegeservice.repository;

import edu.iuh.fit.se.messegeservice.model.VipSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VipSubscriptionRepository extends MongoRepository<VipSubscription, String> {

    Optional<VipSubscription> findByUserId(String userId);

    Optional<VipSubscription> findByUserIdAndStatus(String userId, String status);
}
