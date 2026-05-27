package edu.iuh.fit.se.commonservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Groq API client — tương đương GroqAiService trong file tham khảo.
 * Gọi Groq API (OpenAI-compatible) để sinh MongoDB query và tóm tắt kết quả.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GroqAiService {

    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.groq.model:openai/gpt-oss-120b}")
    private String defaultModel;

    @Value("${ai.groq.base-url:https://api.groq.com/openai/v1/chat/completions}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Gọi Groq API — giống hệt callGroqApi() trong tham khảo.
     *
     * @param messages  Danh sách messages [{role, content}, ...]
     * @param model     Model name (null = dùng default)
     * @return Response text từ LLM
     */
    public String callGroqApi(List<Map<String, String>> messages, String model) {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            throw new RuntimeException("Thiếu cấu hình ai.groq.api-key. Vui lòng cấu hình Groq API key.");
        }

        String useModel = (model != null && !model.isBlank()) ? model : defaultModel;

        try {
            // Build request body (OpenAI-compatible format)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", useModel);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 4096);

            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            log.debug("🤖 [Groq] Calling API: model={}", useModel);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String content = extractContent(jsonResponse);
                log.info("✅ [Groq] Successfully got response from model {}", useModel);
                return content;
            } else {
                log.error("❌ [Groq] API returned error: {}", response.getStatusCode());
                throw new RuntimeException("Groq API error: " + response.getStatusCode());
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ [Groq] Error calling API: {}", e.getMessage(), e);
            throw new RuntimeException("Error communicating with Groq API: " + e.getMessage(), e);
        }
    }

    /**
     * Overload: dùng default model
     */
    public String callGroqApi(List<Map<String, String>> messages) {
        return callGroqApi(messages, null);
    }

    /**
     * Extract content from OpenAI-compatible response:
     * { "choices": [{ "message": { "content": "..." } }] }
     */
    private String extractContent(JsonNode jsonResponse) {
        JsonNode choices = jsonResponse.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message != null) {
                JsonNode content = message.get("content");
                if (content != null) {
                    return content.asText();
                }
            }
        }
        throw new RuntimeException("Invalid Groq API response format");
    }
}
