package edu.iuh.fit.se.messegeservice.controller;

import edu.iuh.fit.se.messegeservice.dto.CallDTO;
import edu.iuh.fit.se.messegeservice.dto.CallActionRequest;
import edu.iuh.fit.se.messegeservice.dto.CallInitiateRequest;
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

    @PostMapping("/initiate")
    public ResponseEntity<CallDTO> initiate(@RequestBody CallInitiateRequest request) {
        CallDTO call = callService.initiateCall(
                request.getConversationId(),
                request.getCallerId(),
                request.getCalleeIds(),
                request.getType()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(call);
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<CallDTO> join(@PathVariable String id, @RequestBody CallActionRequest request) {
        return ResponseEntity.ok(callService.joinCall(id, request.getUserId()));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<CallDTO> end(@PathVariable String id, @RequestBody CallActionRequest request) {
        return ResponseEntity.ok(callService.endCall(id, request.getUserId()));
    }

    @PostMapping("/{id}/missed")
    public ResponseEntity<CallDTO> missed(@PathVariable String id, @RequestBody CallActionRequest request) {
        return ResponseEntity.ok(callService.markMissed(id, request.getUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CallDTO> updateCall(@PathVariable String id, @RequestBody CallDTO callDTO) {
        return ResponseEntity.ok(callService.updateCall(id, callDTO));
    }
}

