package edu.iuh.fit.se.mediaservice.repository;

import edu.iuh.fit.se.mediaservice.model.Gift;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GiftRepository extends MongoRepository<Gift, String> {
    List<Gift> findByActiveTrueOrderBySortOrderAsc();
    List<Gift> findAllByOrderBySortOrderAsc();
}

