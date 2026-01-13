package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.CallDTO;
import edu.iuh.fit.se.messegeservice.model.Call;
import edu.iuh.fit.se.messegeservice.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;

    public List<CallDTO> getCallsByConversationId(String conversationId) {
        return callRepository.findByConversationIdOrderByStartedAtDesc(conversationId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public CallDTO getCallById(String id) {
        return callRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
    }

    public CallDTO createCall(CallDTO callDTO) {
        Call call = toEntity(callDTO);
        call.setStartedAt(LocalDateTime.now());
        if (callDTO.getDurationSeconds() > 0) {
            call.setEndedAt(call.getStartedAt().plusSeconds(callDTO.getDurationSeconds()));
        }
        Call saved = callRepository.save(call);
        return toDTO(saved);
    }

    public CallDTO updateCall(String id, CallDTO callDTO) {
        Call call = callRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
        
        call.setStatus(callDTO.getStatus());
        call.setDurationSeconds(callDTO.getDurationSeconds());
        if (callDTO.getEndedAt() != null) {
            call.setEndedAt(callDTO.getEndedAt());
        }
        
        Call updated = callRepository.save(call);
        return toDTO(updated);
    }

    private CallDTO toDTO(Call call) {
        CallDTO dto = new CallDTO();
        dto.setId(call.getId());
        dto.setConversationId(call.getConversationId());
        dto.setCallerId(call.getCallerId());
        dto.setCalleeIds(call.getCalleeIds());
        dto.setType(call.getType());
        dto.setStatus(call.getStatus());
        dto.setDurationSeconds(call.getDurationSeconds());
        dto.setStartedAt(call.getStartedAt());
        dto.setEndedAt(call.getEndedAt());
        return dto;
    }

    private Call toEntity(CallDTO dto) {
        Call call = new Call();
        call.setConversationId(dto.getConversationId());
        call.setCallerId(dto.getCallerId());
        call.setCalleeIds(dto.getCalleeIds());
        call.setType(dto.getType());
        call.setStatus(dto.getStatus());
        call.setDurationSeconds(dto.getDurationSeconds());
        return call;
    }
}

