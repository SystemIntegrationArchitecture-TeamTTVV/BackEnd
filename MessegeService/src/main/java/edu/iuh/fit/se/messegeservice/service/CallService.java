package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.CallDTO;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.model.Call;
import edu.iuh.fit.se.messegeservice.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallService {

    private final CallRepository callRepository;
    private final CommonServiceClientFacade commonServiceClientFacade;
    private final SocketEmitterService socketEmitterService;

    // ───────── Queries ─────────

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

    // ───────── Initiate (RINGING) ─────────

    public CallDTO initiateCall(String conversationId, String callerId, List<String> calleeIds, String type) {
        // Privacy check for direct calls
        if (calleeIds != null && calleeIds.size() == 1) {
            String calleeId = calleeIds.get(0);
            boolean allowed = commonServiceClientFacade.canCall(callerId, calleeId);
            if (!allowed) {
                log.warn("🚫 [Privacy] Call blocked: {} → {} (callee has FRIENDS_ONLY)", callerId, calleeId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot call: recipient only accepts calls from friends");
            }
        }

        // Determine call type
        boolean isGroup = calleeIds != null && calleeIds.size() > 1;
        String callType = isGroup ? "GROUP" : "DIRECT";

        // Enforce max participants for group calls
        if (isGroup && calleeIds.size() + 1 > Call.MAX_GROUP_PARTICIPANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Group call limited to " + Call.MAX_GROUP_PARTICIPANTS + " participants");
        }

        Call call = new Call();
        call.setConversationId(conversationId);
        call.setCallerId(callerId);
        call.setCalleeIds(calleeIds);
        call.setType(normalizeType(type));
        call.setStatus("RINGING");
        call.setCallType(callType);
        call.setHostId(callerId);
        call.setState("RINGING");
        call.setActiveParticipantIds(new ArrayList<>(List.of(callerId)));
        call.setLeftParticipantIds(new ArrayList<>());
        call.setStartedAt(LocalDateTime.now());
        call.setDurationSeconds(0);

        Call saved = callRepository.save(call);
        log.info("📞 Call initiated: id={}, type={}, callType={}, host={}", saved.getId(), saved.getType(), callType, callerId);
        return toDTO(saved);
    }

    // ───────── Join (RINGING → CONNECTED) ─────────

    public CallDTO joinCall(String id, String userId) {
        Call call = findCallOrThrow(id);
        validateCallNotEnded(call);
        validateParticipant(call, userId);

        // Add to active participants (deduplicate)
        List<String> active = call.getActiveParticipantIds();
        if (active == null) active = new ArrayList<>();
        if (!active.contains(userId)) {
            active.add(userId);
        }
        call.setActiveParticipantIds(active);

        // Remove from left list if was there (rejoin scenario)
        if (call.getLeftParticipantIds() != null) {
            call.getLeftParticipantIds().remove(userId);
        }

        // Transition: RINGING → CONNECTED when first callee joins
        if ("RINGING".equals(call.getState())) {
            call.setState("CONNECTED");
            call.setStatus("ONGOING");
        }

        Call saved = callRepository.save(call);

        // Emit USER_JOINED to all active participants
        emitToActiveParticipants(call, "CALL_USER_JOINED", Map.of(
                "callId", call.getId(),
                "userId", userId,
                "activeParticipantIds", call.getActiveParticipantIds()
        ));

        log.info("📞 User {} joined call {}", userId, id);
        return toDTO(saved);
    }

    // ───────── Leave — Core smart lifecycle logic ─────────

    public CallDTO leaveCall(String id, String userId, String transferToUserId) {
        Call call = findCallOrThrow(id);
        validateCallNotEnded(call);

        List<String> active = call.getActiveParticipantIds() != null
                ? call.getActiveParticipantIds() : new ArrayList<>();

        if (!active.contains(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not in this call");
        }

        boolean isHost = userId.equals(call.getHostId());

        // ── DIRECT CALL: one leaves → end for both ──
        if ("DIRECT".equals(call.getCallType())) {
            return endCallInternal(call, userId);
        }

        // ── GROUP CALL ──

        // Remove user from active
        active.remove(userId);
        call.setActiveParticipantIds(active);
        if (call.getLeftParticipantIds() == null) {
            call.setLeftParticipantIds(new ArrayList<>());
        }
        if (!call.getLeftParticipantIds().contains(userId)) {
            call.getLeftParticipantIds().add(userId);
        }

        // If host is leaving: handle host transfer
        if (isHost) {
            if (transferToUserId != null && active.contains(transferToUserId)) {
                // Transfer host to specified user
                call.setHostId(transferToUserId);
                log.info("👑 Host transferred from {} to {} in call {}", userId, transferToUserId, id);

                // Emit HOST_TRANSFERRED
                emitToActiveParticipants(call, "CALL_HOST_TRANSFERRED", Map.of(
                        "callId", call.getId(),
                        "previousHostId", userId,
                        "newHostId", transferToUserId,
                        "activeParticipantIds", call.getActiveParticipantIds()
                ));
            } else if (!active.isEmpty()) {
                // Auto-transfer to first remaining participant
                String newHost = active.get(0);
                call.setHostId(newHost);
                log.info("👑 Host auto-transferred from {} to {} in call {}", userId, newHost, id);

                emitToActiveParticipants(call, "CALL_HOST_TRANSFERRED", Map.of(
                        "callId", call.getId(),
                        "previousHostId", userId,
                        "newHostId", newHost,
                        "activeParticipantIds", call.getActiveParticipantIds()
                ));
            }
        }

        // Emit USER_LEFT
        emitToActiveParticipants(call, "CALL_USER_LEFT", Map.of(
                "callId", call.getId(),
                "userId", userId,
                "activeParticipantIds", call.getActiveParticipantIds()
        ));

        // Also notify the leaving user
        socketEmitterService.emitToUserById(userId, SocketEventDTO.of(
                "CALL_USER_LEFT", userId, Map.of("callId", call.getId(), "userId", userId, "self", true)));

        // If no one left → end call
        if (active.isEmpty()) {
            return endCallInternal(call, userId);
        }

        Call saved = callRepository.save(call);
        log.info("📞 User {} left group call {}, remaining: {}", userId, id, active.size());
        return toDTO(saved);
    }

    // ───────── End All (host only, group call) ─────────

    public CallDTO endCallForAll(String id, String userId) {
        Call call = findCallOrThrow(id);
        validateCallNotEnded(call);

        // Only host can end for all in group calls
        if ("GROUP".equals(call.getCallType()) && !userId.equals(call.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can end the call for everyone");
        }

        return endCallInternal(call, userId);
    }

    // ───────── End (backward-compatible) ─────────

    public CallDTO endCall(String id, String userId) {
        Call call = findCallOrThrow(id);
        validateParticipant(call, userId);

        // For direct calls or host ending: end for all
        if ("DIRECT".equals(call.getCallType()) || userId.equals(call.getHostId())) {
            return endCallInternal(call, userId);
        }

        // For group non-host: treat as leave
        return leaveCall(id, userId, null);
    }

    // ───────── Rejoin ─────────

    public CallDTO rejoinCall(String id, String userId) {
        Call call = findCallOrThrow(id);

        if ("ENDED".equals(call.getState())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Call has already ended");
        }

        // Must have been a participant
        boolean wasParticipant = (call.getCalleeIds() != null && call.getCalleeIds().contains(userId))
                || userId.equals(call.getCallerId());
        if (!wasParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User was not part of this call");
        }

        List<String> active = call.getActiveParticipantIds() != null
                ? call.getActiveParticipantIds() : new ArrayList<>();

        if (!active.contains(userId)) {
            active.add(userId);
            call.setActiveParticipantIds(active);
        }

        if (call.getLeftParticipantIds() != null) {
            call.getLeftParticipantIds().remove(userId);
        }

        Call saved = callRepository.save(call);

        emitToActiveParticipants(call, "CALL_USER_JOINED", Map.of(
                "callId", call.getId(),
                "userId", userId,
                "rejoin", true,
                "activeParticipantIds", call.getActiveParticipantIds()
        ));

        log.info("📞 User {} rejoined call {}", userId, id);
        return toDTO(saved);
    }

    // ───────── Transfer Host ─────────

    public CallDTO transferHost(String id, String currentHostId, String newHostId) {
        Call call = findCallOrThrow(id);
        validateCallNotEnded(call);

        if (!"GROUP".equals(call.getCallType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host transfer only for group calls");
        }

        if (!currentHostId.equals(call.getHostId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only current host can transfer");
        }

        List<String> active = call.getActiveParticipantIds();
        if (active == null || !active.contains(newHostId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not in the call");
        }

        call.setHostId(newHostId);
        Call saved = callRepository.save(call);

        emitToActiveParticipants(call, "CALL_HOST_TRANSFERRED", Map.of(
                "callId", call.getId(),
                "previousHostId", currentHostId,
                "newHostId", newHostId,
                "activeParticipantIds", call.getActiveParticipantIds()
        ));

        log.info("👑 Host transferred: {} → {} in call {}", currentHostId, newHostId, id);
        return toDTO(saved);
    }

    // ───────── Mark Missed ─────────

    public CallDTO markMissed(String id, String userId) {
        Call call = findCallOrThrow(id);
        validateParticipant(call, userId);
        if (call.getEndedAt() == null) {
            call.setEndedAt(LocalDateTime.now());
        }
        call.setStatus("MISSED");
        if (call.getState() == null || "RINGING".equals(call.getState())) {
            call.setState("ENDED");
        }
        return toDTO(callRepository.save(call));
    }

    // ───────── Update (legacy) ─────────

    public CallDTO updateCall(String id, CallDTO callDTO) {
        Call call = findCallOrThrow(id);
        call.setStatus(callDTO.getStatus());
        call.setDurationSeconds(callDTO.getDurationSeconds());
        if (callDTO.getEndedAt() != null) {
            call.setEndedAt(callDTO.getEndedAt());
        }
        return toDTO(callRepository.save(call));
    }

    // ───────── Get active participants ─────────

    public List<String> getActiveParticipants(String id) {
        Call call = findCallOrThrow(id);
        return call.getActiveParticipantIds() != null ? call.getActiveParticipantIds() : List.of();
    }

    // ═══════════ Internal helpers ═══════════

    private CallDTO endCallInternal(Call call, String triggeredBy) {
        call.setState("ENDED");
        call.setStatus("COMPLETED");
        if (call.getStartedAt() == null) {
            call.setStartedAt(LocalDateTime.now());
        }
        call.setEndedAt(LocalDateTime.now());
        call.setDurationSeconds((int) Math.max(0,
                ChronoUnit.SECONDS.between(call.getStartedAt(), call.getEndedAt())));

        // Notify ALL participants (active + left) that the call ended
        Set<String> allParticipants = new HashSet<>();
        if (call.getActiveParticipantIds() != null) allParticipants.addAll(call.getActiveParticipantIds());
        if (call.getLeftParticipantIds() != null) allParticipants.addAll(call.getLeftParticipantIds());
        allParticipants.add(call.getCallerId());
        if (call.getCalleeIds() != null) allParticipants.addAll(call.getCalleeIds());

        for (String participantId : allParticipants) {
            socketEmitterService.emitToUserById(participantId, SocketEventDTO.of(
                    "CALL_END", participantId, Map.of(
                            "callId", call.getId(),
                            "endedBy", triggeredBy,
                            "callType", call.getCallType() != null ? call.getCallType() : "DIRECT"
                    )));
        }

        Call saved = callRepository.save(call);
        log.info("📴 Call {} ended by {}, duration={}s", call.getId(), triggeredBy, call.getDurationSeconds());
        return toDTO(saved);
    }

    private void emitToActiveParticipants(Call call, String eventType, Map<String, Object> data) {
        List<String> active = call.getActiveParticipantIds();
        if (active == null) return;
        for (String participantId : active) {
            socketEmitterService.emitToUserById(participantId, SocketEventDTO.of(
                    eventType, participantId, data));
        }
    }

    private Call findCallOrThrow(String id) {
        return callRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call not found with id: " + id));
    }

    private void validateCallNotEnded(Call call) {
        if ("ENDED".equals(call.getState())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Call has already ended");
        }
    }

    private void validateParticipant(Call call, String userId) {
        boolean isCaller = userId != null && userId.equals(call.getCallerId());
        boolean isCallee = userId != null && call.getCalleeIds() != null && call.getCalleeIds().contains(userId);
        boolean isActive = userId != null && call.getActiveParticipantIds() != null && call.getActiveParticipantIds().contains(userId);
        if (!isCaller && !isCallee && !isActive) {
            throw new RuntimeException("User is not a participant of this call");
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return "VOICE";
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return "VIDEO".equals(normalized) ? "VIDEO" : "VOICE";
    }

    // ═══════════ Mapping ═══════════

    private CallDTO toDTO(Call call) {
        CallDTO dto = new CallDTO();
        dto.setId(call.getId());
        dto.setConversationId(call.getConversationId());
        dto.setCallerId(call.getCallerId());
        dto.setCalleeIds(call.getCalleeIds());
        dto.setType(call.getType());
        dto.setStatus(call.getStatus());
        dto.setCallType(call.getCallType());
        dto.setHostId(call.getHostId());
        dto.setActiveParticipantIds(call.getActiveParticipantIds());
        dto.setState(call.getState());
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
        call.setCallType(dto.getCallType());
        call.setHostId(dto.getHostId());
        call.setDurationSeconds(dto.getDurationSeconds());
        return call;
    }
}
