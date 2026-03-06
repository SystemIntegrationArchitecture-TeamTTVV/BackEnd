package edu.iuh.fit.se.commonservice.controller;

import edu.iuh.fit.se.commonservice.dto.AIChatRequestDTO;
import edu.iuh.fit.se.commonservice.dto.AIChatResponseDTO;
import edu.iuh.fit.se.commonservice.service.AIChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<AIChatResponseDTO> chat(@RequestBody AIChatRequestDTO request) {
        try {
            AIChatResponseDTO response = aiChatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // Return error response
            AIChatResponseDTO errorResponse = new AIChatResponseDTO(
                    "Xin lỗi, đã xảy ra lỗi: " + e.getMessage(),
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}

