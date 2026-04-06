package edu.iuh.fit.se.commonservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.iuh.fit.se.commonservice.dto.AIChatRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIChatResponseDTO;
import edu.iuh.fit.se.commonservice.dto.AIDailySummaryResponseDTO;
import edu.iuh.fit.se.commonservice.model.Friend;
import edu.iuh.fit.se.commonservice.model.Notification;
import edu.iuh.fit.se.commonservice.model.Post;
import edu.iuh.fit.se.commonservice.repository.FriendRepository;
import edu.iuh.fit.se.commonservice.repository.NotificationRepository;
import edu.iuh.fit.se.commonservice.repository.PostRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatService {

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    // Sử dụng gemini-2.5-flash-lite như user yêu cầu (model nhẹ)
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
        private final NotificationRepository notificationRepository;
        private final FriendRepository friendRepository;
        private final PostRepository postRepository;
        private final MessageServiceClientFacade messageServiceClientFacade;

    public AIChatResponseDTO chat(AIChatRequestDTO request) {
        try {
            return chatAsync(request).join();
        } catch (Exception e) {
            // Preserve previous behavior: bubble up a RuntimeException that will be handled by controller advice (if any)
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Error communicating with AI service: " + e.getMessage(), e);
        }
    }

    public String generatePostContent(String idea, String userId) {
        if (idea == null || idea.isBlank()) {
            throw new RuntimeException("Prompt cannot be empty");
        }

        String prompt = "Bạn là trợ lý viết bài mạng xã hội. "
                + "Hãy viết 1 bài đăng tiếng Việt ngắn gọn, tự nhiên, có cảm xúc tích cực và dễ đọc. "
                + "Không thêm lời dẫn kiểu AI, không markdown, không tiêu đề phụ. "
                + "Giữ dưới 180 từ. Ý tưởng người dùng: " + idea;

        AIChatRequestDTO request = new AIChatRequestDTO(prompt, userId, null);
        AIChatResponseDTO response = chat(request);
        return response.getResponse();
    }

    public AIDailySummaryResponseDTO summarizeDailyActivity(String userId, Integer limit) {
        if (userId == null || userId.isBlank()) {
            throw new RuntimeException("Thiếu userId để tóm tắt hoạt động trong ngày.");
        }

        int maxItems = (limit == null || limit <= 0) ? 6 : Math.min(limit, 15);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        List<Notification> allNotificationsToday = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> n.getCreatedAt() != null && !n.getCreatedAt().isBefore(startOfDay))
                .collect(Collectors.toList());

        List<Notification> notificationsForPrompt = allNotificationsToday.stream()
                .limit(maxItems)
                .collect(Collectors.toList());

        List<Friend> friends = friendRepository.findByUserId(userId);
        List<Post> allFriendPostsToday = new ArrayList<>();

        for (Friend friend : friends) {
            if (friend.getFriendId() == null || friend.getFriendId().isBlank()) {
                continue;
            }
            List<Post> friendPosts = postRepository.findByAuthorIdOrderByCreatedAtDesc(friend.getFriendId());
            friendPosts.stream()
                    .filter(post -> !post.isDeleted())
                    .filter(post -> post.getCreatedAt() != null && !post.getCreatedAt().isBefore(startOfDay))
                    .forEach(allFriendPostsToday::add);
        }

        allFriendPostsToday.sort(Comparator.comparing(Post::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Post> friendPostsForPrompt = allFriendPostsToday.stream().limit(maxItems).collect(Collectors.toList());

        List<Map<String, Object>> incomingMessagesForPrompt = collectIncomingMessagesToday(userId, startOfDay, maxItems);

        String prompt = buildDailySummaryPrompt(userId, notificationsForPrompt, friendPostsForPrompt, incomingMessagesForPrompt);
        String aiSummary = chat(new AIChatRequestDTO(prompt, userId, null)).getResponse();

        if (aiSummary == null || aiSummary.isBlank()) {
            aiSummary = "Hôm nay chưa có nhiều hoạt động mới để tóm tắt.";
        }

        return new AIDailySummaryResponseDTO(
                aiSummary,
                allNotificationsToday.size(),
                allFriendPostsToday.size(),
                incomingMessagesForPrompt.size(),
                LocalDateTime.now()
        );
    }

    @TimeLimiter(name = "aiService", fallbackMethod = "chatFallbackAsync")
    @Bulkhead(name = "aiService", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "chatFallbackAsync")
    @RateLimiter(name = "aiService", fallbackMethod = "chatFallbackAsync")
    @Retry(name = "aiService", fallbackMethod = "chatFallbackAsync")
    @CircuitBreaker(name = "aiService", fallbackMethod = "chatFallbackAsync")
    public CompletableFuture<AIChatResponseDTO> chatAsync(AIChatRequestDTO request) {
        return CompletableFuture.supplyAsync(() -> {
        try {
            log.info("🤖 [AIChat] Processing chat request from user: {}", request.getUserId());

            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                throw new RuntimeException("Thiếu cấu hình ai.gemini.api-key. Vui lòng cấu hình API key mới.");
            }

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
            
        } catch (HttpClientErrorException.Forbidden e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("❌ [AIChat] Gemini 403 Forbidden: {}", responseBody);

            if (responseBody != null && responseBody.toLowerCase().contains("reported as leaked")) {
                throw new RuntimeException("API key Gemini đã bị Google khóa vì lộ. Vui lòng tạo key mới và cập nhật ai.gemini.api-key.");
            }
            throw new RuntimeException("Gemini từ chối truy cập (403). Vui lòng kiểm tra API key và quyền truy cập model.");
        } catch (HttpClientErrorException e) {
            log.error("❌ [AIChat] HTTP error calling Gemini API: {}", e.getStatusCode(), e);
            throw new RuntimeException("AI service returned HTTP error: " + e.getStatusCode(), e);
        } catch (JsonProcessingException e) {
            log.error("❌ [AIChat] Failed to parse Gemini response JSON", e);
            throw new RuntimeException("Invalid response format from AI service", e);
        } catch (RuntimeException e) {
            log.error("❌ [AIChat] Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Error communicating with AI service: " + e.getMessage(), e);
        }
        });
    }

    @SuppressWarnings("unused")
    private CompletableFuture<AIChatResponseDTO> chatFallbackAsync(AIChatRequestDTO request, Throwable throwable) {
        log.warn("⚠️ [AIChat] Falling back for chat due to: {}", throwable.getMessage());
        String fallbackMessage = "Xin lỗi, dịch vụ AI hiện đang bận hoặc tạm thời không khả dụng. "
                + "Bạn vui lòng thử lại sau nhé.";
        String conversationId = request.getConversationId() != null
                ? request.getConversationId()
                : generateConversationId(request.getUserId());
        return CompletableFuture.completedFuture(new AIChatResponseDTO(fallbackMessage, conversationId));
    }

    private List<Map<String, Object>> collectIncomingMessagesToday(String userId, LocalDateTime startOfDay, int maxItems) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> conversations = messageServiceClientFacade.getConversationsByUserId(userId);

        for (Map<String, Object> conversation : conversations) {
            if (result.size() >= maxItems) {
                break;
            }
            String conversationId = asString(conversation.get("id"));
            if (conversationId == null || conversationId.isBlank()) {
                continue;
            }

            List<Map<String, Object>> messages = messageServiceClientFacade.getMessagesByConversationId(conversationId);
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, Object> message = messages.get(i);
                String senderId = asString(message.get("senderId"));
                String content = asString(message.get("content"));
                LocalDateTime createdAt = parseLocalDateTime(message.get("createdAt"));

                if (createdAt == null || createdAt.isBefore(startOfDay)) {
                    continue;
                }
                if (senderId == null || senderId.equals(userId)) {
                    continue;
                }
                if (content == null || content.isBlank()) {
                    continue;
                }

                Map<String, Object> item = new HashMap<>();
                item.put("conversationName", resolveConversationName(conversation));
                item.put("senderName", fallback(asString(message.get("senderName")), "Người dùng"));
                item.put("content", sanitize(content, 220));
                item.put("createdAt", createdAt.format(DateTimeFormatter.ofPattern("HH:mm")));
                result.add(item);
                break;
            }
        }

        return result;
    }

    private String buildDailySummaryPrompt(
            String userId,
            List<Notification> notifications,
            List<Post> friendPosts,
            List<Map<String, Object>> incomingMessages
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý tóm tắt hoạt động mạng xã hội trong ngày cho người dùng. ");
        sb.append("Hãy viết tiếng Việt tự nhiên, rõ ràng, ngắn gọn, không markdown. ");
        sb.append("Cấu trúc bắt buộc gồm 4 phần theo thứ tự: \n");
        sb.append("1) Tổng quan nhanh\n");
        sb.append("2) Thông báo hôm nay\n");
        sb.append("3) Bạn bè đã đăng gì/làm gì\n");
        sb.append("4) Tin nhắn nhận được (ai nhắn, nội dung chính)\n\n");

        sb.append("userId: ").append(userId).append("\n");
        sb.append("Ngày: ").append(LocalDate.now()).append("\n\n");

        sb.append("[Dữ liệu thông báo]\n");
        if (notifications.isEmpty()) {
            sb.append("- Không có thông báo mới hôm nay.\n");
        } else {
            for (Notification n : notifications) {
                String actor = n.getActor() != null ? fallback(n.getActor().getFullName(), "Ai đó") : "Ai đó";
                String title = sanitize(fallback(n.getTitle(), "(không có tiêu đề)"), 120);
                String content = sanitize(fallback(n.getContent(), ""), 180);
                String time = n.getCreatedAt() != null ? n.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";
                sb.append("- ").append(time).append(" | ").append(actor).append(" | ").append(title);
                if (!content.isBlank()) {
                    sb.append(" | ").append(content);
                }
                sb.append("\n");
            }
        }

        sb.append("\n[Dữ liệu bài đăng bạn bè]\n");
        if (friendPosts.isEmpty()) {
            sb.append("- Hôm nay chưa thấy bài đăng mới từ bạn bè.\n");
        } else {
            for (Post post : friendPosts) {
                String friendName = (post.getAuthor() != null)
                        ? fallback(post.getAuthor().getFullName(), "Bạn bè")
                        : "Bạn bè";
                String content = sanitize(fallback(post.getContent(), "(không có nội dung text)"), 220);
                String activity = sanitize(fallback(post.getActivity(), ""), 80);
                String feeling = sanitize(fallback(post.getFeeling(), ""), 80);
                String time = post.getCreatedAt() != null ? post.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--";

                sb.append("- ").append(time).append(" | ").append(friendName).append(" | ").append(content);
                if (!activity.isBlank()) {
                    sb.append(" | hoạt động: ").append(activity);
                }
                if (!feeling.isBlank()) {
                    sb.append(" | cảm xúc: ").append(feeling);
                }
                sb.append("\n");
            }
        }

        sb.append("\n[Dữ liệu tin nhắn nhận được]\n");
        if (incomingMessages.isEmpty()) {
            sb.append("- Không có tin nhắn đến mới hôm nay.\n");
        } else {
            for (Map<String, Object> msg : incomingMessages) {
                sb.append("- ")
                        .append(fallback(asString(msg.get("createdAt")), "--:--"))
                        .append(" | cuộc trò chuyện: ")
                        .append(fallback(asString(msg.get("conversationName")), "Tin nhắn"))
                        .append(" | ")
                        .append(fallback(asString(msg.get("senderName")), "Người dùng"))
                        .append(": ")
                        .append(fallback(asString(msg.get("content")), ""))
                        .append("\n");
            }
        }

        sb.append("\nYêu cầu phong cách:\n");
        sb.append("- Không bịa dữ liệu ngoài phần đã cung cấp.\n");
        sb.append("- Nêu rõ ai làm gì, ưu tiên các ý đáng chú ý.\n");
        sb.append("- Nếu một mục không có dữ liệu, nói ngắn gọn là chưa có cập nhật.\n");

        return sb.toString();
    }

    private String resolveConversationName(Map<String, Object> conversation) {
        String groupName = asString(conversation.get("groupName"));
        if (groupName != null && !groupName.isBlank()) {
            return groupName;
        }

        Object participantNamesObj = conversation.get("participantNames");
        if (participantNamesObj instanceof List<?> participantNames && !participantNames.isEmpty()) {
            String names = participantNames.stream()
                    .map(String::valueOf)
                    .filter(n -> !n.isBlank())
                    .limit(3)
                    .collect(Collectors.joining(", "));
            if (!names.isBlank()) {
                return names;
            }
        }
        return "Tin nhắn";
    }

    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof String timeString) {
            try {
                return LocalDateTime.parse(timeString);
            } catch (Exception ignored) {
                try {
                    return OffsetDateTime.parse(timeString).toLocalDateTime();
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String fallback(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String sanitize(String value, int maxLength) {
        String normalized = fallback(value, "").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim() + "...";
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

