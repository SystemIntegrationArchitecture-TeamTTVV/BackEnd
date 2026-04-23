package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.CallDTO;
import edu.iuh.fit.se.messegeservice.model.Call;
import edu.iuh.fit.se.messegeservice.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final CommonServiceClientFacade commonServiceClientFacade;

    public List<CallDTO> getCallsByConversationId(String conversationId) {
        return callRepository.findByConversationIdOrderByStartedAtDesc(conversationId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CallDTO> getCallsByCallerId(String callerId) {
        return callRepository.findByCallerIdOrderByStartedAtDesc(callerId).stream()
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

    public CallDTO initiateCall(String conversationId, String callerId, List<String> calleeIds, String type) {
        // ── Privacy check: stranger call blocking ──
        if (calleeIds != null && calleeIds.size() == 1) {
            String calleeId = calleeIds.get(0);
            boolean allowed = commonServiceClientFacade.canCall(callerId, calleeId);
            if (!allowed) {
                log.warn("🚫 [Privacy] Call blocked: {} → {} (callee has FRIENDS_ONLY)", callerId, calleeId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot call: recipient only accepts calls from friends");
            }
        }

        Call call = new Call();
        call.setConversationId(conversationId);
        call.setCallerId(callerId);
        call.setCalleeIds(calleeIds);
        call.setType(normalizeType(type));
        call.setStatus("RINGING");
        call.setStartedAt(LocalDateTime.now());
        call.setDurationSeconds(0);
        return toDTO(callRepository.save(call));
    }

    public CallDTO joinCall(String id, String userId) {
        Call call = callRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
        validateParticipant(call, userId);
        if ("RINGING".equals(call.getStatus())) {
            call.setStatus("ONGOING");
        }
        return toDTO(callRepository.save(call));
    }

    public CallDTO endCall(String id, String userId) {
        Call call = callRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
        validateParticipant(call, userId);
        call.setStatus("COMPLETED");
        if (call.getStartedAt() == null) {
            call.setStartedAt(LocalDateTime.now());
        }
        call.setEndedAt(LocalDateTime.now());
        call.setDurationSeconds((int) Math.max(0, ChronoUnit.SECONDS.between(call.getStartedAt(), call.getEndedAt())));
        return toDTO(callRepository.save(call));
    }

    public CallDTO markMissed(String id, String userId) {
        Call call = callRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
        validateParticipant(call, userId);
        if (call.getEndedAt() == null) {
            call.setEndedAt(LocalDateTime.now());
        }
        call.setStatus("MISSED");
        return toDTO(callRepository.save(call));
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

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "VOICE";
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("VIDEO".equals(normalized)) {
            return "VIDEO";
        }
        return "VOICE";
    }

    private void validateParticipant(Call call, String userId) {
        boolean isCaller = userId != null && userId.equals(call.getCallerId());
        boolean isCallee = userId != null && call.getCalleeIds() != null && call.getCalleeIds().contains(userId);
        if (!isCaller && !isCallee) {
            throw new RuntimeException("User is not a participant of this call");
        }
    }
}

