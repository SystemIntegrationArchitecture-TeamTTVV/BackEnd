package edu.iuh.fit.se.commonservice.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid Embedding Service — Gemini API (chính) + TF-IDF local (fallback).
 *
 * Chiến lược:
 * 1. Ưu tiên gọi Gemini embedding API (gemini-embedding-001) — hiểu ngữ nghĩa sâu
 * 2. Nếu API lỗi/timeout → tự động fallback sang TF-IDF local — vẫn hoạt động bình thường
 *
 * Kết quả: hệ thống embedding KHÔNG BAO GIỜ CHẾT, luôn trả về vector hợp lệ.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    // === Gemini Embedding API ===
    private static final String EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";
    private static final int GEMINI_DIM = 768;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // === Local TF-IDF Fallback ===
    private static final int LOCAL_DIM = 768; // Giữ cùng dimension để vector tương thích

    private static final Set<String> STOP_WORDS = Set.of(
        "tôi", "của", "là", "có", "không", "được", "và", "các", "một", "cho",
        "này", "với", "đã", "trong", "từ", "về", "để", "khi", "trên", "vào",
        "bị", "nào", "ra", "thì", "lại", "sẽ", "hay", "hoặc", "như", "đến",
        "cũng", "còn", "mà", "theo", "rồi", "đó", "ở", "do", "bởi", "vì",
        "hãy", "bạn", "anh", "chị", "em", "ơi", "nhé", "nha", "ạ", "à",
        "the", "is", "are", "was", "be", "have", "has", "had", "does",
        "a", "an", "at", "by", "for", "in", "of", "on", "to", "up", "it"
    );

    /**
     * Embed text thành vector — ưu tiên Gemini API, fallback TF-IDF local.
     *
     * @param text Đoạn text cần embed
     * @return List<Double> vector (768 dims)
     */
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        // === Thử Gemini API trước (ngữ nghĩa sâu, chất lượng cao) ===
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                List<Double> geminiVector = embedWithGemini(text);
                if (!geminiVector.isEmpty()) {
                    return geminiVector;
                }
            } catch (Exception e) {
                log.warn("⚠️ [Embedding] Gemini API failed, falling back to local TF-IDF: {}", e.getMessage());
            }
        }

        // === Fallback: TF-IDF local (luôn hoạt động, không bao giờ lỗi) ===
        log.debug("🔄 [Embedding] Using local TF-IDF fallback for: {}...",
                text.substring(0, Math.min(50, text.length())));
        return embedLocal(text);
    }

    // ═══════════════════════════════════════════════════════════════
    // Gemini Embedding API
    // ═══════════════════════════════════════════════════════════════

    private List<Double> embedWithGemini(String text) {
        Map<String, Object> part = new HashMap<>();
        part.put("text", text);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "models/gemini-embedding-001");
        requestBody.put("content", content);
        requestBody.put("taskType", "RETRIEVAL_QUERY");
        requestBody.put("outputDimensionality", GEMINI_DIM);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = EMBEDDING_URL + "?key=" + geminiApiKey;
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, httpEntity, String.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                List<Double> vector = extractGeminiEmbedding(jsonResponse);
                if (!vector.isEmpty()) {
                    log.debug("✅ [Embedding] Gemini vector ({} dims) for: {}...",
                            vector.size(), text.substring(0, Math.min(50, text.length())));
                }
                return vector;
            } catch (Exception e) {
                log.error("❌ [Embedding] Error parsing Gemini response: {}", e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    private List<Double> extractGeminiEmbedding(JsonNode jsonResponse) {
        JsonNode embedding = jsonResponse.get("embedding");
        if (embedding != null) {
            JsonNode values = embedding.get("values");
            if (values != null && values.isArray()) {
                List<Double> result = new ArrayList<>(values.size());
                for (JsonNode value : values) {
                    result.add(value.asDouble());
                }
                return result;
            }
        }
        return new ArrayList<>();
    }

    // ═══════════════════════════════════════════════════════════════
    // Local TF-IDF + Feature Hashing Fallback
    // ═══════════════════════════════════════════════════════════════

    private List<Double> embedLocal(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return new ArrayList<>();
        }

        double[] vector = new double[LOCAL_DIM];

        // Unigrams
        for (String token : tokens) {
            int idx = Math.abs(token.hashCode()) % LOCAL_DIM;
            vector[idx] += 1.0;
        }

        // Bigrams — quan trọng cho tiếng Việt ("bài viết", "bạn bè", "bình luận")
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + "_" + tokens.get(i + 1);
            int idx = Math.abs(bigram.hashCode()) % LOCAL_DIM;
            vector[idx] += 1.5;
        }

        // L2 Normalize
        double norm = 0;
        for (double v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        }

        List<Double> result = new ArrayList<>(LOCAL_DIM);
        for (double v : vector) result.add(v);
        return result;
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(
                text.toLowerCase()
                    .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                    .split("\\s+"))
            .filter(w -> !w.isBlank() && w.length() > 1 && !STOP_WORDS.contains(w))
            .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // Cosine Similarity
    // ═══════════════════════════════════════════════════════════════

    /**
     * Tính Cosine Similarity giữa 2 vectors.
     */
    public static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
