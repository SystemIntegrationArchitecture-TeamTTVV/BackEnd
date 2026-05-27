package edu.iuh.fit.se.messegeservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    @Value("${ai.gemini.violation.api-key:}")
    private String geminiViolationApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    public ModerationResult moderate(String text) {
        if (text == null || text.isBlank()) {
            return new ModerationResult(false, "No content");
        }
        
        if (geminiViolationApiKey == null || geminiViolationApiKey.isBlank()) {
            log.warn("⚠️ [ModerationService] Missing Gemini API Key. Bypassing moderation.");
            return new ModerationResult(false, "API Key missing");
        }

        String prompt = """
            Bạn là hệ thống kiểm duyệt nội dung.

            Hãy đánh giá xem bình luận sau đây có vi phạm tiêu chuẩn cộng đồng không.
            Các loại vi phạm:
            - Chửi thề, xúc phạm, miệt thị
            - Bạo lực
            - Nội dung 18+
            - Phân biệt đối xử
            - Khuyến khích hành vi phạm pháp
            - Spam, quảng cáo

            ❗❗ YÊU CẦU BẮT BUỘC ❗❗
            ➜ Trả về DUY NHẤT JSON với dạng sau, không thêm chữ nào khác:
            {
              "violate": true/false,
              "reason": "giải thích ngắn gọn"
            }

            Bình luận:
            "%s"
        """.formatted(text);

        try {
            String url = GEMINI_URL + "?key=" + geminiViolationApiKey;

            Map<String, Object> requestBody = new HashMap<>();
            
            List<Map<String, Object>> contentsList = new ArrayList<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            
            Map<String, Object> contentObj = new HashMap<>();
            contentObj.put("parts", new Object[]{part});
            contentsList.add(contentObj);
            requestBody.put("contents", contentsList);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String aiResponse = extractTextFromResponse(mapper.readTree(response.getBody()));
                log.debug("RAW_AI_OUTPUT = {}", aiResponse);
                
                Matcher m = Pattern.compile("\\{.*?\\}", Pattern.DOTALL).matcher(aiResponse);
                if (m.find()) {
                    String jsonPart = m.group(0);
                    JsonNode node = mapper.readTree(jsonPart);
                    boolean violate = node.has("violate") && node.get("violate").asBoolean();
                    String reason = node.has("reason") ? node.get("reason").asText() : "";
                    return new ModerationResult(violate, reason);
                } else {
                    log.warn("JSON not found in AI response: {}", aiResponse);
                    // Default to true/block if AI acts weird to be safe, or false to pass. 
                    // Usually better to block or bypass based on policy. Let's bypass if parse error.
                    return new ModerationResult(false, "Parse failed");
                }
            } else {
                log.error("Gemini API error: {}", response.getStatusCode());
                return new ModerationResult(true, "Hệ thống kiểm duyệt đang bận — vui lòng thử lại.");
            }
        } catch (Exception e) {
            log.error("❌ Moderation failed!", e);
            // Block message if system errors out
            return new ModerationResult(true, "Hệ thống kiểm duyệt đang bận — vui lòng thử lại.");
        }
    }

    private String extractTextFromResponse(JsonNode jsonResponse) {
        try {
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
            return "";
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            return "";
        }
    }

    public record ModerationResult(boolean violate, String reason) {}
}
