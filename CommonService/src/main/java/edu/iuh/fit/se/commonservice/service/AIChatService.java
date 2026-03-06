package edu.iuh.fit.se.commonservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import edu.iuh.fit.se.commonservice.dto.AIChatRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIChatResponseDTO;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    @Value("${ai.gemini.api-key:AIzaSyBkpBpE22FyiNaMwwVJ_8UYmAiWlj-xGQw}")
    private String geminiApiKey;

    // Sử dụng gemini-2.5-flash-lite như user yêu cầu (model nhẹ)
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "aiService", fallbackMethod = "chatFallback")
    public AIChatResponseDTO chat(AIChatRequestDTO request) {
        try {
            log.info("🤖 [AIChat] Processing chat request from user: {}", request.getUserId());

            // Build request body for Gemini API
            Map<String, Object> requestBody = new HashMap<>();
            
            // Contents array
            Map<String, Object> part = new HashMap<>();
            part.put("text", request.getMessage());
            
            Map<String, Object> role = new HashMap<>();
            role.put("parts", new Object[]{part});
            role.put("role", "user");
            
            requestBody.put("contents", new Object[]{role});

            // Build HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String url = GEMINI_URL + "?key=" + geminiApiKey;
            
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
            
            log.debug("🤖 [AIChat] Calling Gemini API: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Parse response
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                
                // Extract text from response
                String aiResponse = extractTextFromResponse(jsonResponse);
                
                log.info("✅ [AIChat] Successfully got response from Gemini");
                
                return new AIChatResponseDTO(
                        aiResponse,
                        request.getConversationId() != null ? request.getConversationId() : generateConversationId(request.getUserId())
                );
            } else {
                log.error("❌ [AIChat] Gemini API returned error: {}", response.getStatusCode());
                throw new RuntimeException("Failed to get response from AI: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("❌ [AIChat] Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Error communicating with AI service: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private AIChatResponseDTO chatFallback(AIChatRequestDTO request, Throwable throwable) {
        log.warn("⚠️ [AIChat] Falling back for chat due to: {}", throwable.getMessage());
        String fallbackMessage = "Xin lỗi, dịch vụ AI hiện đang bận hoặc tạm thời không khả dụng. "
                + "Bạn vui lòng thử lại sau nhé.";
        String conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : generateConversationId(request.getUserId());
        return new AIChatResponseDTO(fallbackMessage, conversationId);
    }

    private String extractTextFromResponse(JsonNode jsonResponse) {
        try {
            // Gemini API response structure:
            // {
            //   "candidates": [
            //     {
            //       "content": {
            //         "parts": [
            //           { "text": "..." }
            //         ]
            //       }
            //     }
            //   ]
            // }
            
            JsonNode candidates = jsonResponse.get("candidates");
            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode content = firstCandidate.get("content");
                if (content != null) {
                    JsonNode parts = content.get("parts");
                    if (parts != null && parts.isArray() && parts.size() > 0) {
                        JsonNode firstPart = parts.get(0);
                        JsonNode text = firstPart.get("text");
                        if (text != null) {
                            return text.asText();
                        }
                    }
                }
            }
            
            // Fallback: return error message
            return "Xin lỗi, tôi không thể xử lý câu hỏi này lúc này. Vui lòng thử lại sau.";
        } catch (Exception e) {
            log.error("❌ [AIChat] Error parsing Gemini response: {}", e.getMessage());
            return "Xin lỗi, đã xảy ra lỗi khi xử lý phản hồi từ AI.";
        }
    }

    private String generateConversationId(String userId) {
        // Simple conversation ID generation
        return userId != null ? "ai_" + userId + "_" + System.currentTimeMillis() : "ai_" + System.currentTimeMillis();
    }
}

