package edu.iuh.fit.se.commonservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.iuh.fit.se.commonservice.dto.AIChatResponseDTO;
import edu.iuh.fit.se.commonservice.model.QueryExample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Data Query Service — Orchestrator 3 bước, tương đương handleDataQueryMode()
 * trong StudentChatService.java tham khảo.
 *
 * Luồng:
 * 1. RAG: Embed câu hỏi → tìm similar examples → few-shot
 * 2. LLM sinh MongoDB Aggregation Pipeline JSON
 * 3. Firewall validate + execute pipeline
 * 4. LLM tóm tắt kết quả bằng tiếng Việt
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataQueryService {

    private final GroqAiService groqAiService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final MongoQueryFirewall queryFirewall;
    private final ObjectMapper objectMapper;

    /**
     * Main entry point — tương đương StudentChatService.handleDataQueryMode()
     */
    public AIChatResponseDTO processDataQuery(String message, String userId) {
        log.info("📊 [DataQuery] Processing query from user {}: {}", userId,
                message.substring(0, Math.min(80, message.length())));

        // === RAG Phase: Embed + Tìm similar examples ===
        List<Double> queryVector = List.of();
        List<QueryExample> fewShotExamples = List.of();
        try {
            queryVector = embeddingService.embed(message);
            if (!queryVector.isEmpty()) {
                fewShotExamples = vectorSearchService.findSimilar(queryVector, 3);
            }
        } catch (Exception e) {
            log.warn("⚠️ [DataQuery] RAG phase failed (non-critical): {}", e.getMessage());
        }

        // === Bước 1: LLM sinh MongoDB Pipeline JSON ===
        // (tương đương Step 1 trong tham khảo: Generate SQL using LLM)
        String generatedQueryJson;
        String collection;
        String pipelineJson;
        try {
            generatedQueryJson = generateMongoQuery(message, userId, fewShotExamples);

            // Kiểm tra invalid request (tương đương INVALID_REQUEST check trong tham khảo)
            if (generatedQueryJson.startsWith("INVALID_REQUEST:")) {
                String errorMsg = generatedQueryJson.substring(16).trim();
                return new AIChatResponseDTO(errorMsg, null, null, null, "DATA_QUERY");
            }

            // Clean markdown nếu LLM lỡ sinh (tương đương tham khảo)
            generatedQueryJson = cleanLlmOutput(generatedQueryJson);
            log.info("📊 [DataQuery] Generated query:\n{}", generatedQueryJson);

            // Parse collection + pipeline
            JsonNode queryNode = objectMapper.readTree(generatedQueryJson);
            if (queryNode.has("error")) {
                return new AIChatResponseDTO(
                        queryNode.get("error").asText(),
                        null, null, null, "DATA_QUERY"
                );
            }

            collection = queryNode.has("collection") ? queryNode.get("collection").asText() : null;
            pipelineJson = queryNode.has("pipeline") ? queryNode.get("pipeline").toString() : null;

            if (collection == null || pipelineJson == null) {
                return new AIChatResponseDTO(
                        "❌ AI không thể phân tích câu hỏi này thành truy vấn dữ liệu. Vui lòng thử lại với câu hỏi rõ ràng hơn.",
                        null, null, null, "DATA_QUERY"
                );
            }

        } catch (Exception e) {
            log.error("❌ [DataQuery] Error generating query: {}", e.getMessage(), e);
            return new AIChatResponseDTO(
                    "❌ Lỗi: AI không thể phân tích câu hỏi lúc này.\nChi tiết: " + e.getMessage(),
                    null, null, null, "DATA_QUERY"
            );
        }

        // === Bước 2: Firewall validate + execute ===
        // (tương đương Step 2 trong tham khảo: Execute SQL via core-service)
        List<Map<String, Object>> data;
        try {
            List<Document> pipeline = queryFirewall.parsePipeline(pipelineJson);
            String validationError = queryFirewall.validate(pipeline, collection, userId);

            if (validationError != null) {
                log.warn("🔒 [DataQuery] Firewall rejected query: {}", validationError);
                // Lưu RAG (failed)
                saveFewShot(message, generatedQueryJson, collection, queryVector, false);
                return new AIChatResponseDTO(
                        "❌ Truy vấn bị hệ thống bảo mật từ chối:\n" + validationError,
                        null, null, null, "DATA_QUERY"
                );
            }

            data = queryFirewall.execute(pipeline, collection);

        } catch (Exception e) {
            log.error("❌ [DataQuery] Error executing query: {}", e.getMessage());
            saveFewShot(message, generatedQueryJson, collection, queryVector, false);
            return new AIChatResponseDTO(
                    "❌ Đã xảy ra lỗi khi truy vấn dữ liệu:\n" + e.getMessage(),
                    null, null, null, "DATA_QUERY"
            );
        }

        // === Bước 3: LLM tóm tắt kết quả bằng tiếng Việt ===
        // (tương đương Step 3 trong tham khảo: Use LLM to formulate natural language answer)
        String finalAnswer;
        try {
            finalAnswer = summarizeResults(message, generatedQueryJson, data);
        } catch (Exception e) {
            log.error("❌ [DataQuery] Error summarizing results: {}", e.getMessage());
            finalAnswer = buildFallbackAnswer(message, data);
        }

        // === Lưu RAG (thành công) ===
        saveFewShot(message, generatedQueryJson, collection, queryVector, true);

        return new AIChatResponseDTO(finalAnswer, null, null, data, "DATA_QUERY");
    }

    /**
     * Bước 1: Sinh MongoDB Aggregation Pipeline JSON bằng LLM.
     * Tương đương phần sqlGenerationPrompt trong tham khảo.
     */
    private String generateMongoQuery(String message, String userId, List<QueryExample> fewShotExamples) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một AI chuyển đổi ngôn ngữ tự nhiên thành MongoDB Aggregation Pipeline.\n\n");
        prompt.append(MongoSchemaContext.SCHEMA).append("\n\n");

        // Few-shot examples từ RAG (tham khảo không có, đây là enhancement)
        if (fewShotExamples != null && !fewShotExamples.isEmpty()) {
            prompt.append("Các ví dụ tương tự trước đó (tham khảo):\n");
            for (int i = 0; i < fewShotExamples.size(); i++) {
                QueryExample ex = fewShotExamples.get(i);
                prompt.append("Ví dụ ").append(i + 1).append(":\n");
                prompt.append("  Câu hỏi: ").append(ex.getQuestion()).append("\n");
                prompt.append("  Pipeline: ").append(ex.getPipeline()).append("\n\n");
            }
        }

        prompt.append("Yêu cầu BẮT BUỘC (tuân thủ để vượt qua Security Firewall):\n");
        prompt.append("0. PHÂN TÍCH CÂU HỎI: Nếu câu hỏi vô nghĩa (vd: 'aaaaa', '123'), không liên quan đến truy vấn dữ liệu, ");
        prompt.append("hoặc là câu chào hỏi đơn thuần, BẮT BUỘC trả về: INVALID_REQUEST: <giải thích tiếng Việt>\n");
        prompt.append("1. CHỈ trả về JSON hợp lệ theo format: {\"collection\": \"<tên>\", \"pipeline\": [<stages>]}\n");
        prompt.append("2. Pipeline PHẢI bắt đầu bằng {\"$match\": {\"authorId\": \"").append(userId).append("\", ...}} ");
        prompt.append("(hoặc userId/recipientId/sellerId tùy collection).\n");
        prompt.append("3. Luôn thêm \"isDeleted\": false khi query posts, comments, videos.\n");
        prompt.append("4. KHÔNG dùng $out, $merge, $delete, $function, $where.\n");
        prompt.append("5. PHẢI có {\"$limit\": 100} cuối pipeline.\n");
        prompt.append("6. Dùng $project để chọn cột cần, KHÔNG trả toàn bộ document.\n");
        prompt.append("7. Khi đếm: dùng [{\"$count\": \"total\"}] hoặc [{\"$group\": {\"_id\": null, \"count\": {\"$sum\": 1}}}].\n");
        prompt.append("8. Khi lọc thời gian 'hôm nay': dùng {\"createdAt\": {\"$gte\": {\"$date\": \"<today ISO>\"}}}\n");
        prompt.append("9. Khi lọc thời gian 'tháng này': dùng {\"createdAt\": {\"$gte\": {\"$date\": \"<first day of month ISO>\"}}}\n");
        prompt.append("10. CHỈ trả về JSON hoặc INVALID_REQUEST, KHÔNG CÓ VĂN BẢN NÀO KHÁC.\n\n");
        prompt.append("userId hiện tại: ").append(userId).append("\n");
        prompt.append("Câu hỏi của người dùng: ").append(message);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", "You are an expert MongoDB aggregation pipeline generator."),
                Map.of("role", "user", "content", prompt.toString())
        );

        return groqAiService.callGroqApi(messages).trim();
    }

    /**
     * Bước 3: LLM tóm tắt kết quả.
     * Tương đương phần finalAnswerPrompt trong tham khảo.
     */
    private String summarizeResults(String question, String generatedQuery, List<Map<String, Object>> data) {
        // Giới hạn data gửi cho LLM (tương đương summarizedSample trong tham khảo)
        int totalRows = data != null ? data.size() : 0;
        Object summaryData = data;
        boolean summarized = false;
        if (data != null && data.size() > 10) {
            summaryData = data.subList(0, 5);
            summarized = true;
        }

        String dataJson;
        try {
            dataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summaryData);
        } catch (Exception e) {
            dataJson = String.valueOf(summaryData);
        }

        // Prompt tóm tắt — tương đương finalAnswerPrompt trong tham khảo
        String prompt = "Bạn là trợ lý AI. Dựa vào câu hỏi của người dùng và dữ liệu JSON truy xuất từ MongoDB dưới đây, hãy trả lời câu hỏi bằng ngôn ngữ tự nhiên.\n" +
                "Yêu cầu:\n" +
                (summarized
                        ? "- Dữ liệu đưa cho bạn chỉ là 5 bản ghi đầu tiên trong tổng số " + totalRows + " bản ghi. Hãy nói rõ đây là tóm tắt một số bản ghi.\n"
                        : "- Phải liệt kê đầy đủ các mục tìm thấy trong dữ liệu được cung cấp.\n") +
                "- Chỉ tóm tắt các nội dung chính có ích cho người dùng.\n" +
                "- Không hiển thị ID nội bộ hoặc thông tin kỹ thuật: _id, ObjectId, embedding, pipeline.\n" +
                "- Trình bày ngắn gọn, dễ hiểu, tối đa 5 ý chính.\n" +
                "- Nếu không tìm thấy dữ liệu, trả lời thân thiện.\n" +
                "- KHÔNG trả lời bằng raw JSON.\n" +
                "- Trả lời bằng tiếng Việt.\n\n" +
                "Câu hỏi: " + question + "\n" +
                "Pipeline đã dùng (tham khảo): " + generatedQuery + "\n" +
                "Dữ liệu JSON: " + dataJson;

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );

        return groqAiService.callGroqApi(messages);
    }

    /**
     * Clean markdown output từ LLM (tương đương tham khảo).
     */
    private String cleanLlmOutput(String output) {
        if (output == null) return "";
        String cleaned = output.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.contains("```")) {
            cleaned = cleaned.split("```")[0].trim();
        }
        return cleaned.trim();
    }

    /**
     * Fallback answer khi LLM tóm tắt thất bại (tương đương buildFallbackAnswer trong tham khảo).
     */
    private String buildFallbackAnswer(String question, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return "Hệ thống không tìm thấy kết quả nào phù hợp với câu hỏi của bạn.";
        }
        if (data.size() == 1) {
            Map<String, Object> row = new java.util.HashMap<>(data.get(0));
            row.remove("_id"); // Ignore group by null _id
            if (row.size() == 1) {
                Object val = row.values().iterator().next();
                return "Kết quả tìm kiếm cho câu hỏi của bạn là: " + val;
            }
        }
        return "Hệ thống tìm thấy " + data.size() + " kết quả phù hợp cho câu hỏi: " + question
                + ". Bạn có thể xem bảng dữ liệu chi tiết bên dưới.";
    }

    /**
     * Lưu few-shot example vào RAG (async, non-blocking).
     */
    private void saveFewShot(String question, String query, String collection,
                             List<Double> embedding, boolean success) {
        try {
            vectorSearchService.saveExample(question, query, collection, embedding, success);
        } catch (Exception e) {
            log.warn("⚠️ [DataQuery] Failed to save few-shot example: {}", e.getMessage());
        }
    }
}
