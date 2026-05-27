package edu.iuh.fit.se.commonservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import edu.iuh.fit.se.commonservice.model.AiConsultationLog;

public interface AiConsultationLogRepository extends MongoRepository<AiConsultationLog, String> {
}
