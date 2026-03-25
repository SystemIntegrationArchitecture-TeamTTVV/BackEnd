package edu.iuh.fit.se.commonservice.service;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIViolationCheckService {

    /**
     * API key riêng cho phần kiểm duyệt vi phạm.
     * Nếu không set riêng thì sẽ fallback sang `ai.gemini.api-key` (để tránh service bị chết).
     */
    @Value("${ai.gemini.violation.api-key:${ai.gemini.api-key:}}")
    private String violationApiKey;

    // Model lite để tiết kiệm token.
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern IS_VIOLATION_BOOL_PATTERN =
            Pattern.compile("\"isViolation\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    public void checkOrThrow(String rawText, String targetType) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isBlank()) return;
        if (targetType == null || targetType.isBlank()) targetType = "CONTENT";

        // Trimming đầu vào để giảm token tiêu thụ.
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }

        if (violationApiKey == null || violationApiKey.isBlank()) {
            // Không set key thì không check được => fail-open để không phá hệ thống.
            log.warn("[AIViolationCheck] Missing `ai.gemini.violation.api-key`. Skip moderation.");
            return;
        }

        String prompt = ""
                + "Bạn là bộ lọc nội dung cho mạng xã hội. "
                + "Nhiệm vụ: xác định xem nội dung có vi phạm chính sách hay không.\n"
                + "Các nhóm vi phạm cần chú ý: bạo lực/cực đoan, khiêu dâm, quấy rối/cưỡng ép, kích động thù hằn, "
                + "tiết lộ thông tin cá nhân nhạy cảm, gian lận, và ngôn từ độc hại.\n"
                + "Trả về DUY NHẤT một JSON hợp lệ (không code block, không chữ nào khác) theo schema:\n"
                + "{"
                + "\"isViolation\": boolean,"
                + "\"category\": string,"
                + "\"severity\": integer,"
                + "\"reason\": string"
                + "}\n"
                + "targetType: " + targetType + "\n"
                + "Nội dung cần kiểm tra:\n"
                + text;

        String resultText = callGemini(prompt);
        if (resultText == null || resultText.isBlank()) {
            // Đã có key nhưng không lấy được kết quả => chặn luôn cho đúng kỳ vọng.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI moderation không trả về kết quả hợp lệ. Vui lòng thử lại sau.");
        }
        ViolationDecision decision = parseDecision(resultText);

        if (decision != null && decision.isViolation) {
            String reason = (decision.reason == null || decision.reason.isBlank())
                    ? "Nội dung có dấu hiệu vi phạm."
                    : decision.reason;

            String categoryPart = (decision.category == null || decision.category.isBlank())
                    ? ""
                    : " (" + decision.category + ")";

            String severityPart = decision.severity > 0 ? " (mức độ " + decision.severity + ")" : "";

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nội dung vi phạm" + categoryPart + severityPart + ": " + reason
            );
        }
    }

    private String callGemini(String message) {
        try {
            Map<String, Object> requestBody = new HashMap<>();

            Map<String, Object> part = new HashMap<>();
            part.put("text", message);

            Map<String, Object> role = new HashMap<>();
            role.put("parts", new Object[]{part});
            role.put("role", "user");

            requestBody.put("contents", new Object[]{role});

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = GEMINI_URL + "?key=" + violationApiKey;
            HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

            log.debug("[AIViolationCheck] Calling Gemini moderation API");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String rawBody = response.getBody();
                JsonNode jsonResponse = objectMapper.readTree(rawBody);
                String extracted = extractTextFromResponse(jsonResponse);
                if (extracted != null && !extracted.isBlank()) {
                    return extracted;
                }
                // Fallback: trả raw JSON body để parseDecision vẫn có cơ hội bắt JSON.
                log.warn("[AIViolationCheck] Could not extract moderation text; using raw response body for parsing.");
                return rawBody;
            }

            throw new RuntimeException("Gemini moderation failed: " + response.getStatusCode());
        } catch (HttpClientErrorException.Forbidden e) {
            String body = e.getResponseBodyAsString();
            log.error("[AIViolationCheck] 403 Forbidden: {}", body);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini moderation bị từ chối (403). Kiểm tra API key và quyền truy cập model.");
        } catch (Exception e) {
            log.error("[AIViolationCheck] Error calling Gemini moderation: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Lỗi khi gọi AI moderation. Vui lòng thử lại sau.");
        }
    }

    private ViolationDecision parseDecision(String resultText) {
        if (resultText == null) return null;
        String s = resultText.trim();
        if (s.isEmpty()) return null;

        // Try parse JSON object inside the model output.
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                String json = s.substring(start, end + 1);
                JsonNode node = objectMapper.readTree(json);
                boolean isViolation = node.path("isViolation").asBoolean(false);
                String category = node.path("category").isMissingNode() ? null : node.path("category").asText(null);
                int severity = node.path("severity").asInt(1);
                String reason = node.path("reason").isMissingNode() ? null : node.path("reason").asText(null);
                return new ViolationDecision(isViolation, category, severity, reason);
            } catch (Exception e) {
                log.warn("[AIViolationCheck] Failed to parse JSON from model output. Will fallback heuristics.");
            }
        }

        // Heuristic fallback: chỉ cần bắt được isViolation true/false từ text/JSON.
        Matcher m = IS_VIOLATION_BOOL_PATTERN.matcher(s);
        if (m.find()) {
            boolean isViolation = Boolean.parseBoolean(m.group(1));
            return new ViolationDecision(isViolation, null, isViolation ? 3 : 1, null);
        }

        return new ViolationDecision(false, null, 1, null);
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
        } catch (Exception e) {
            log.warn("[AIViolationCheck] Could not extract moderation text: {}", e.getMessage());
        }
        return "";
    }

    private static class ViolationDecision {
        private final boolean isViolation;
        private final String category;
        private final int severity;
        private final String reason;

        private ViolationDecision(boolean isViolation, String category, int severity, String reason) {
            this.isViolation = isViolation;
            this.category = category;
            this.severity = severity;
            this.reason = reason;
        }
    }
}

