package edu.iuh.fit.se.commonservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import edu.iuh.fit.se.commonservice.model.AiConsultationSettings;

public interface AiConsultationSettingsRepository extends MongoRepository<AiConsultationSettings, String> {
}
