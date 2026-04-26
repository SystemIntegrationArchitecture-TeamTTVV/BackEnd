package edu.iuh.fit.se.commonservice.repository;

import edu.iuh.fit.se.commonservice.model.Report;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {
    List<Report> findByStatus(String status);
    List<Report> findByTargetType(String targetType);
    List<Report> findByReporterId(String reporterId);
}
