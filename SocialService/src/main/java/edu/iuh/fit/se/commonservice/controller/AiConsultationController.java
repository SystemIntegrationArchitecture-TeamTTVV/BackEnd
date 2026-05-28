package edu.iuh.fit.se.commonservice.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.iuh.fit.se.commonservice.dto.AiConsultationSummaryDTO;
import edu.iuh.fit.se.commonservice.dto.StartCallRequestDTO;
import edu.iuh.fit.se.commonservice.dto.StartCallResponseDTO;
import edu.iuh.fit.se.commonservice.dto.UpdateLogResultDTO;
import edu.iuh.fit.se.commonservice.model.AiConsultationLog;
import edu.iuh.fit.se.commonservice.model.AiConsultationSettings;
import edu.iuh.fit.se.commonservice.service.AiConsultationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai-consultation")
@RequiredArgsConstructor
public class AiConsultationController {

    private final AiConsultationService aiConsultationService;

    @GetMapping("/logs")
    public ResponseEntity<List<AiConsultationLog>> getLogs() {
        return ResponseEntity.ok(aiConsultationService.getLogs());
    }

    @GetMapping("/summary")
    public ResponseEntity<AiConsultationSummaryDTO> getSummary() {
        return ResponseEntity.ok(aiConsultationService.getSummary());
    }

    @GetMapping("/settings")
    public ResponseEntity<AiConsultationSettings> getSettings() {
        return ResponseEntity.ok(aiConsultationService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Boolean>> saveSettings(@RequestBody AiConsultationSettings settings) {
        aiConsultationService.saveSettings(settings);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/call")
    public ResponseEntity<StartCallResponseDTO> startCall(@RequestBody StartCallRequestDTO request) {
        return ResponseEntity.ok(aiConsultationService.startCall(request));
    }

    @PutMapping("/logs/{id}")
    public ResponseEntity<Map<String, Boolean>> updateLogResult(
            @PathVariable String id,
            @RequestBody UpdateLogResultDTO request) {
        aiConsultationService.updateLogResult(id, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping(value = "/twiml/{id}", produces = "application/xml")
    public ResponseEntity<String> getTwiML(@PathVariable String id) {
        return ResponseEntity.ok(aiConsultationService.generateTwiML(id));
    }
}
