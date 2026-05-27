package edu.iuh.fit.se.commonservice.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG storage — lưu trữ các câu hỏi + MongoDB pipeline đã thành công
 * kèm vector embedding để tìm few-shot examples cho câu hỏi mới.
 */
@Document(collection = "query_examples")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryExample {
    @Id
    private String id;

    private String question;        // Câu hỏi gốc của user
    private String pipeline;        // MongoDB pipeline JSON đã sinh
    private String collection;      // Collection chính được query
    private List<Double> embedding; // Vector 768d từ Gemini Embedding
    private boolean success;        // Pipeline có chạy thành công không
    private LocalDateTime createdAt;
}
