package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Stories;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StoriesRepository extends MongoRepository<Stories, String> {


    List<Stories> findByUserIdInAndExpiredAtAfterAndActiveTrue(
            List<String> userIds,
            LocalDateTime now
    );

    // Lấy tất cả active stories chưa hết hạn
    List<Stories> findByActiveTrueAndExpiredAtAfterOrderByCreatedAtDesc(LocalDateTime now);
}

