package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.CallDTO;
import edu.iuh.fit.se.messegeservice.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<CallDTO>> getCallsByConversationId(@PathVariable String conversationId) {
        return ResponseEntity.ok(callService.getCallsByConversationId(conversationId));
    }

    @GetMapping("/caller/{callerId}")
    public ResponseEntity<List<CallDTO>> getCallsByCallerId(@PathVariable String callerId) {
        return ResponseEntity.ok(callService.getCallsByCallerId(callerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CallDTO> getCallById(@PathVariable String id) {
        return ResponseEntity.ok(callService.getCallById(id));
    }

    @PostMapping
    public ResponseEntity<CallDTO> createCall(@RequestBody CallDTO callDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(callService.createCall(callDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CallDTO> updateCall(@PathVariable String id, @RequestBody CallDTO callDTO) {
        return ResponseEntity.ok(callService.updateCall(id, callDTO));
    }
}

