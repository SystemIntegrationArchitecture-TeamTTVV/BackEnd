package edu.iuh.fit.se.commonservice.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.iuh.fit.se.commonservice.dto.AIAutoPostRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIAutoPostResponseDTO;
import edu.iuh.fit.se.commonservice.dto.AIChatRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIChatResponseDTO;
import edu.iuh.fit.se.commonservice.dto.AIDailySummaryRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIDailySummaryResponseDTO;
import edu.iuh.fit.se.commonservice.dto.PostDTO;
import edu.iuh.fit.se.commonservice.service.AIChatService;
import edu.iuh.fit.se.commonservice.service.DataQueryService;
import edu.iuh.fit.se.commonservice.service.PostService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;
    private final DataQueryService dataQueryService;
    private final PostService postService;

    @PostMapping("/chat")
    public ResponseEntity<AIChatResponseDTO> chat(@RequestBody AIChatRequestDTO request) {
        try {
            String msg = request.getMessage() != null ? request.getMessage().toLowerCase() : "";
            boolean isDataQueryRequest = false;

            // Check if the message contains data query keywords
            if (msg.contains("bao nhiêu") || msg.contains("mấy") || msg.contains("đếm") || msg.contains("liệt kê") 
                || msg.contains("danh sách") || msg.contains("thông báo") || msg.contains("bài viết") 
                || msg.contains("bài post") || msg.contains("bài đăng") || msg.contains("post")
                || msg.contains("bạn bè") || msg.contains("sản phẩm") || msg.contains("bình luận") 
                || msg.contains("comment") || msg.contains("like") || msg.contains("thích") || msg.contains("tương tác") 
                || msg.contains("lưu") || msg.contains("tin nhắn") || msg.contains("group") 
                || msg.contains("nhóm") || msg.contains("react") || msg.contains("story")
                || msg.contains("tin đăng") || msg.contains("chợ") || msg.contains("đăng bán")
                || msg.contains("notification") || msg.contains("friend")) {
                isDataQueryRequest = true;
            }

            if (isDataQueryRequest || "DATA_QUERY".equalsIgnoreCase(request.getMode())) {
                AIChatResponseDTO queryResponse = dataQueryService.processDataQuery(
                        request.getMessage(), request.getUserId()
                );
                
                // If it successfully generated query and retrieved data, return it
                if (queryResponse.getData() != null || queryResponse.getGeneratedQuery() != null) {
                    return ResponseEntity.ok(queryResponse);
                }
            }

            // Otherwise, fall back to standard CHAT mode (Gemini)
            AIChatResponseDTO response = aiChatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Return error response
            AIChatResponseDTO errorResponse = new AIChatResponseDTO(
                    "Xin lỗi, đã xảy ra lỗi: " + e.getMessage(),
                    null, null, null, null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/auto-post")
    public ResponseEntity<?> autoPost(@RequestBody AIAutoPostRequestDTO request) {
        try {
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Thiếu userId để đăng bài tự động."));
            }
            if (request.getPrompt() == null || request.getPrompt().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Bạn chưa nhập nội dung ý tưởng để AI viết bài."));
            }

            String generatedContent = aiChatService.generatePostContent(request.getPrompt(), request.getUserId());

            PostDTO postDTO = new PostDTO();
            postDTO.setAuthorId(request.getUserId());
            postDTO.setContent(generatedContent);
            postDTO.setVisibility(request.getVisibility() != null ? request.getVisibility() : "PUBLIC");
            postDTO.setAllowComments(true);
            postDTO.setAllowSharing(true);

            PostDTO createdPost = postService.createPost(postDTO);
            return ResponseEntity.ok(new AIAutoPostResponseDTO(generatedContent, createdPost));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Tự động đăng bài thất bại."));
        }
    }

    @PostMapping("/draft-post")
    public ResponseEntity<?> draftPost(@RequestBody AIAutoPostRequestDTO request) {
        try {
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Thiếu userId để tạo bản nháp."));
            }
            if (request.getPrompt() == null || request.getPrompt().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Bạn chưa nhập nội dung ý tưởng để AI viết nháp."));
            }

            String generatedContent = aiChatService.generatePostContent(request.getPrompt(), request.getUserId());

            return ResponseEntity.ok(Map.of(
                    "generatedContent", generatedContent,
                    "visibility", request.getVisibility() != null ? request.getVisibility() : "PUBLIC"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Tạo bản nháp thất bại."));
        }
    }

    @PostMapping("/daily-summary")
    public ResponseEntity<?> dailySummary(@RequestBody AIDailySummaryRequestDTO request) {
        try {
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Thiếu userId để tóm tắt hoạt động hôm nay."));
            }

            AIDailySummaryResponseDTO response = aiChatService.summarizeDailyActivity(
                    request.getUserId(),
                    request.getLimit()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Không thể tóm tắt hoạt động hôm nay."));
        }
    }
}

