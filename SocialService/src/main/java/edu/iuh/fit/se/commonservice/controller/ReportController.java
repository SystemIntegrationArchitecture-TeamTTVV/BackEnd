package edu.iuh.fit.se.commonservice.controller;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import edu.iuh.fit.se.commonservice.model.Report;
import edu.iuh.fit.se.commonservice.repository.ReportRepository;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    @Autowired
    private ReportRepository reportRepository;

    @GetMapping
    public ResponseEntity<List<ReportDTO>> getAllReports() {
        try {
            List<Report> reports = reportRepository.findAll();
            List<ReportDTO> reportDTOs = reports.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reportDTOs);
        } catch (Exception e) {
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ReportDTO>> getPendingReports() {
        try {
            List<Report> reports = reportRepository.findByStatus("pending");
            List<ReportDTO> reportDTOs = reports.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(reportDTOs);
        } catch (Exception e) {
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Object>> getReportStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        try {
            List<Report> allReports = reportRepository.findAll();
            
            stats.put("totalReports", allReports.size());
            stats.put("pendingReports", allReports.stream()
                    .filter(r -> "pending".equals(r.getStatus()))
                    .count());
            stats.put("reviewingReports", allReports.stream()
                    .filter(r -> "reviewing".equals(r.getStatus()))
                    .count());
            stats.put("resolvedReports", allReports.stream()
                    .filter(r -> "resolved".equals(r.getStatus()))
                    .count());
            stats.put("rejectedReports", allReports.stream()
                    .filter(r -> "rejected".equals(r.getStatus()))
                    .count());
        } catch (Exception e) {
            stats.put("error", "Lỗi khi tải thống kê báo cáo: " + e.getMessage());
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportDTO> getReportById(@PathVariable String id) {
        try {
            java.util.Optional<Report> report = reportRepository.findById(id);
            if (report.isPresent()) {
                return ResponseEntity.ok(convertToDTO(report.get()));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<ReportDTO> createReport(@RequestBody ReportDTO reportDTO) {
        try {
            Report report = convertToEntity(reportDTO);
            report.setStatus("pending");
            report.setCreatedAt(LocalDateTime.now());
            
            Report savedReport = reportRepository.save(report);
            return ResponseEntity.status(HttpStatus.CREATED).body(convertToDTO(savedReport));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateReportStatus(
            @PathVariable String id,
            @RequestParam String status) {
        try {
            java.util.Optional<Report> report = reportRepository.findById(id);
            
            if (!report.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Report reportEntity = report.get();
            reportEntity.setStatus(status);
            Report updatedReport = reportRepository.save(reportEntity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Report status updated successfully");
            response.put("report", convertToDTO(updatedReport));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Helper methods
    private ReportDTO convertToDTO(Report report) {
        ReportDTO dto = new ReportDTO();
        dto.id = report.getId() != null ? report.getId().toString() : "";
        dto.type = report.getTargetType() != null ? report.getTargetType() : "";
        dto.reason = report.getReason() != null ? report.getReason() : "";
        dto.reporterName = report.getReporterId() != null ? report.getReporterId() : "Anonymous";
        dto.reporterAvatar = "";
        dto.targetName = report.getTargetId() != null ? report.getTargetId() : "";
        dto.targetType = report.getTargetType() != null ? report.getTargetType() : "";
        dto.targetId = report.getTargetId() != null ? report.getTargetId() : "";
        dto.status = report.getStatus() != null ? report.getStatus() : "OPEN";
        dto.priority = "medium";
        dto.createdAt = report.getCreatedAt() != null ? report.getCreatedAt() : LocalDateTime.now();
        return dto;
    }

    private Report convertToEntity(ReportDTO dto) {
        Report report = new Report();
        report.setTargetType(dto.targetType);
        report.setReason(dto.reason);
        report.setReporterId(dto.reporterName);
        report.setTargetId(dto.targetId);
        report.setDetail(dto.reason);
        return report;
    }

    @Data
    public static class ReportDTO {
        public String id;
        public String type;
        public String reason;
        public String reporterName;
        public String reporterAvatar;
        public String targetName;
        public String targetType;
        public String targetId;
        public String status;
        public String priority;
        public LocalDateTime createdAt;

        public ReportDTO(String id, String type, String reason, String reporterName, 
                        String reporterAvatar, String targetName, String targetType, 
                        String targetId, String status, String priority, LocalDateTime createdAt) {
            this.id = id;
            this.type = type;
            this.reason = reason;
            this.reporterName = reporterName;
            this.reporterAvatar = reporterAvatar;
            this.targetName = targetName;
            this.targetType = targetType;
            this.targetId = targetId;
            this.status = status;
            this.priority = priority;
            this.createdAt = createdAt;
        }

        public ReportDTO() {}
    }
}
