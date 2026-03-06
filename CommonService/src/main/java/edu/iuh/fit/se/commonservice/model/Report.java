package edu.iuh.fit.se.commonservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    @Id
    private String id;

    @DBRef
    private User reporter;
    private String reporterId;

    private String targetId;   // ID của post, comment, user, group, page, event
    private String targetType; // POST, COMMENT, USER, GROUP, PAGE, EVENT, MESSAGE

    private String reason;     // Lý do report
    private String detail;     // Mô tả chi tiết

    private String status = "pending"; // pending, reviewing, resolved, rejected
    private String actionTaken;     // Ghi chú xử lý

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

