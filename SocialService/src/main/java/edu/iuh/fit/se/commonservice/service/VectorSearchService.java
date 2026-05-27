package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.iuh.fit.se.commonservice.model.QueryExample;
import edu.iuh.fit.se.commonservice.repository.QueryExampleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG Vector Search Service — tìm câu hỏi tương tự trong lịch sử
 * để cung cấp few-shot examples cho LLM sinh query chính xác hơn.
 *
 * Hỗ trợ 2 mode:
 * - MongoDB Atlas Vector Search ($vectorSearch) nếu có index
 * - Fallback: In-memory cosine similarity nếu không có vector index
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

    private final QueryExampleRepository queryExampleRepository;

    /**
     * Tìm top-K query examples tương tự nhất bằng cosine similarity (in-memory).
     * Fallback khi không có MongoDB Atlas Vector Search index.
     */
    public List<QueryExample> findSimilar(List<Double> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty()) {
            log.debug("🔍 [VectorSearch] Empty query vector, returning empty results");
            return List.of();
        }

        try {
            // Load successful examples
            List<QueryExample> allExamples = queryExampleRepository.findTop50BySuccessTrueOrderByCreatedAtDesc();

            if (allExamples.isEmpty()) {
                log.debug("🔍 [VectorSearch] No examples in database");
                return List.of();
            }

            // Tính cosine similarity và sort
            List<QueryExample> results = allExamples.stream()
                    .filter(ex -> ex.getEmbedding() != null && !ex.getEmbedding().isEmpty())
                    .sorted(Comparator.comparingDouble(
                            (QueryExample ex) -> EmbeddingService.cosineSimilarity(queryVector, ex.getEmbedding())
                    ).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());

            log.info("🔍 [VectorSearch] Found {} similar examples (top-{})", results.size(), topK);
            return results;

        } catch (Exception e) {
            log.error("❌ [VectorSearch] Error finding similar examples: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Lưu query example mới kèm embedding vào MongoDB.
     */
    public void saveExample(String question, String pipeline, String collection,
                            List<Double> embedding, boolean success) {
        try {
            QueryExample example = new QueryExample();
            example.setQuestion(question);
            example.setPipeline(pipeline);
            example.setCollection(collection);
            example.setEmbedding(embedding);
            example.setSuccess(success);
            example.setCreatedAt(LocalDateTime.now());

            queryExampleRepository.save(example);
            log.info("💾 [VectorSearch] Saved query example: {} (success={})", 
                     question.substring(0, Math.min(50, question.length())), success);

        } catch (Exception e) {
            log.error("❌ [VectorSearch] Error saving example: {}", e.getMessage());
        }
    }
}
