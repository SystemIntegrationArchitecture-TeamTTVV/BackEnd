package edu.iuh.fit.se.commonservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import edu.iuh.fit.se.commonservice.model.AiConsultationLog;
import java.util.List;

public interface AiConsultationLogRepository extends MongoRepository<AiConsultationLog, String> {
    List<AiConsultationLog> findByUserIdAndStatus(String userId, String status);
}
