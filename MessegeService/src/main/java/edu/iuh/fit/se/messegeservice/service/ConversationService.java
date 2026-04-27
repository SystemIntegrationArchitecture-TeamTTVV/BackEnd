package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.dto.ConversationMetaUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.ConversationPinRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupMemberUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupRoleUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.JoinRequestUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.LeaveGroupRequest;
import edu.iuh.fit.se.messegeservice.dto.RemoveMemberRequest;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.HiddenConversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.HiddenConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final PasswordEncoder PIN_ENCODER = new BCryptPasswordEncoder();
    private static final String TYPE_SYSTEM = "SYSTEM";
    private static final String TYPE_POLL = "POLL";
    private static final int PREVIEW_SCAN_PAGE_SIZE = 50;
    private static final int PREVIEW_SCAN_MAX_PAGES = 20;

    private final ConversationRepository conversationRepository;
    private final HiddenConversationRepository hiddenConversationRepository;
    private final MessageRepository messageRepository;
    private final RestTemplate restTemplate;
    private final SocketEmitterService socketEmitterService;
    private final CommonServiceClientFacade commonServiceClientFacade;
    private final MessageService messageService;
    
    @Value("${common.service.url:http://localhost:8081}")
    private String commonServiceUrl;

    public List<ConversationDTO> getConversationsByUserId(String userId) {
        List<HiddenConversation> visibilityRows = hiddenConversationRepository.findByUserId(userId);
        Set<String> hiddenConversationIds = visibilityRows.stream()
            .filter(HiddenConversation::isHidden)
                .map(HiddenConversation::getConversationId)
                .collect(Collectors.toSet());
        java.util.Map<String, HiddenConversation> visibilityByConversationId = visibilityRows.stream()
            .collect(Collectors.toMap(HiddenConversation::getConversationId, row -> row, (a, b) -> a));

        List<Conversation> conversations = conversationRepository
                .findByParticipantIdsContainingOrderByLastMessageAtDesc(userId)
                .stream()
                .filter(conversation -> !hiddenConversationIds.contains(conversation.getId()))
                .collect(Collectors.toList());

        // ── Batch-prefetch all participant info in ONE call ──
        Map<String, UserDTO> userCache = batchPrefetchUsers(conversations);

        return conversations.stream()
                .map(conversation -> {
                    ConversationDTO dto = toDTOWithCache(conversation, userCache);
                    HiddenConversation visibility = visibilityByConversationId.get(conversation.getId());
                    LocalDateTime clearCutoff = visibility != null ? visibility.getClearBeforeAt() : null;
                    // Recompute preview only for conversations that were user-cleared.
                    // For normal conversations, use persisted lastMessage fields to keep listing fast.
                    if (clearCutoff != null) {
                        applyUserVisibleLastMessage(dto, userId, clearCutoff);
                    }
                    dto.setHiddenForCurrentUser(false);
                    dto.setHiddenRequiresPin(false);
                    dto.setClearBeforeAt(clearCutoff);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<ConversationDTO> getHiddenConversationsByUserId(String userId) {
        List<HiddenConversation> hiddenRows = hiddenConversationRepository.findByUserIdAndHiddenTrue(userId);
        Set<String> hiddenConvIds = hiddenRows.stream()
            .map(HiddenConversation::getConversationId)
            .collect(Collectors.toSet());

        if (hiddenConvIds.isEmpty()) {
            return new ArrayList<>();
        }

        java.util.Map<String, HiddenConversation> hiddenByConvId = hiddenRows.stream()
            .collect(Collectors.toMap(HiddenConversation::getConversationId, row -> row, (a, b) -> a));

        return conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(userId).stream()
                .filter(c -> hiddenConvIds.contains(c.getId()))
                .map(conversation -> {
                    ConversationDTO dto = toDTO(conversation);
                    HiddenConversation hc = hiddenByConvId.get(conversation.getId());
                    dto.setHiddenForCurrentUser(true);
                    dto.setHiddenRequiresPin(hc != null && hc.isRequirePinUnlock());
                    dto.setClearBeforeAt(hc != null ? hc.getClearBeforeAt() : null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<ConversationDTO> searchGroupConversations(String userId, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            return new ArrayList<>();
        }

        List<HiddenConversation> hiddenRows = hiddenConversationRepository.findByUserId(userId);
        Set<String> hiddenConversationIds = hiddenRows.stream()
            .filter(HiddenConversation::isHidden)
            .map(HiddenConversation::getConversationId)
            .collect(Collectors.toSet());
        java.util.Map<String, HiddenConversation> hiddenByConversationId = hiddenRows.stream()
            .collect(Collectors.toMap(HiddenConversation::getConversationId, row -> row, (a, b) -> a));

        return conversationRepository
                .findByParticipantIdsContainingAndIsGroupTrueAndGroupNameContainingIgnoreCaseOrderByLastMessageAtDesc(
                        userId,
                        normalized
                )
                .stream()
                .map(conversation -> {
                    ConversationDTO dto = toDTO(conversation);
                    HiddenConversation visibility = hiddenByConversationId.get(conversation.getId());
                    LocalDateTime clearCutoff = visibility != null ? visibility.getClearBeforeAt() : null;
                    if (clearCutoff != null) {
                        applyUserVisibleLastMessage(dto, userId, clearCutoff);
                    }
                    if (visibility != null) {
                        dto.setHiddenForCurrentUser(visibility.isHidden());
                        dto.setHiddenRequiresPin(visibility.isRequirePinUnlock());
                        dto.setClearBeforeAt(visibility.getClearBeforeAt());
                    } else {
                        dto.setHiddenForCurrentUser(false);
                        dto.setHiddenRequiresPin(false);
                        dto.setClearBeforeAt(null);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public ConversationDTO hideConversation(String conversationId, ConversationPinRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.getPin() == null || request.getPin().trim().length() < 4) {
            throw new IllegalArgumentException("PIN must be at least 4 characters");
        }
        ensureParticipant(conversation, request.getUserId());

        HiddenConversation hiddenConversation = hiddenConversationRepository
                .findByUserIdAndConversationId(request.getUserId(), conversationId)
                .orElseGet(HiddenConversation::new);

        hiddenConversation.setUserId(request.getUserId());
        hiddenConversation.setConversationId(conversationId);
        hiddenConversation.setHidden(true);
        hiddenConversation.setPinHash(PIN_ENCODER.encode(request.getPin().trim()));
        hiddenConversation.setRequirePinUnlock(true);
        hiddenConversation.setLastAccessAt(LocalDateTime.now());
        if (hiddenConversation.getCreatedAt() == null) {
            hiddenConversation.setCreatedAt(LocalDateTime.now());
        }
        hiddenConversation.setUpdatedAt(LocalDateTime.now());

        hiddenConversationRepository.save(hiddenConversation);

        ConversationDTO dto = toDTO(conversation);
        dto.setHiddenForCurrentUser(true);
        dto.setHiddenRequiresPin(true);
        dto.setClearBeforeAt(hiddenConversation.getClearBeforeAt());
        return dto;
    }

    public ConversationDTO unhideConversation(String conversationId, ConversationPinRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.getPin() == null || request.getPin().trim().isBlank()) {
            throw new IllegalArgumentException("PIN is required");
        }
        ensureParticipant(conversation, request.getUserId());

        HiddenConversation hiddenConversation = hiddenConversationRepository
                .findByUserIdAndConversationId(request.getUserId(), conversationId)
                .orElseThrow(() -> new IllegalArgumentException("No hidden conversation found for this user"));

        if (!hiddenConversation.isRequirePinUnlock()) {
            throw new IllegalArgumentException("This conversation does not require PIN unlock");
        }

        if (hiddenConversation.getPinHash() == null || !PIN_ENCODER.matches(request.getPin().trim(), hiddenConversation.getPinHash())) {
            throw new IllegalArgumentException("Invalid PIN");
        }

        hiddenConversation.setHidden(false);
        hiddenConversation.setRequirePinUnlock(false);
        hiddenConversation.setLastAccessAt(LocalDateTime.now());
        hiddenConversation.setUpdatedAt(LocalDateTime.now());
        hiddenConversationRepository.save(hiddenConversation);

        ConversationDTO dto = toDTO(conversation);
        dto.setHiddenForCurrentUser(false);
        dto.setHiddenRequiresPin(false);
        dto.setClearBeforeAt(hiddenConversation.getClearBeforeAt());
        return dto;
    }

    public ConversationDTO clearConversationForUser(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        ensureParticipant(conversation, userId);

        HiddenConversation visibility = hiddenConversationRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .orElseGet(HiddenConversation::new);

        visibility.setUserId(userId);
        visibility.setConversationId(conversationId);
        visibility.setHidden(true);
        visibility.setRequirePinUnlock(false);
        visibility.setPinHash(null);
        visibility.setClearBeforeAt(LocalDateTime.now());
        visibility.setLastAccessAt(LocalDateTime.now());
        if (visibility.getCreatedAt() == null) {
            visibility.setCreatedAt(LocalDateTime.now());
        }
        visibility.setUpdatedAt(LocalDateTime.now());
        hiddenConversationRepository.save(visibility);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("clearBeforeAt", visibility.getClearBeforeAt() != null ? visibility.getClearBeforeAt().toString() : null);
        socketEmitterService.emitToUserById(
            userId,
            SocketEventDTO.of(SocketEventTypes.CONVERSATION_CLEARED, userId, payload)
        );

        ConversationDTO dto = toDTO(conversation);
        dto.setHiddenForCurrentUser(true);
        dto.setHiddenRequiresPin(false);
        dto.setClearBeforeAt(visibility.getClearBeforeAt());
        return dto;
    }

    public ConversationDTO restoreConversation(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        ensureParticipant(conversation, userId);

        HiddenConversation visibility = hiddenConversationRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .orElse(null);

        if (visibility != null) {
            if (visibility.isRequirePinUnlock()) {
                throw new IllegalArgumentException("This conversation is PIN-locked. Use unhide with PIN.");
            }
            visibility.setHidden(false);
            visibility.setLastAccessAt(LocalDateTime.now());
            visibility.setUpdatedAt(LocalDateTime.now());
            hiddenConversationRepository.save(visibility);

            socketEmitterService.emitToUserById(
                    userId,
                    SocketEventDTO.of(
                            SocketEventTypes.CONVERSATION_RESTORED,
                            userId,
                            java.util.Map.of("conversationId", conversationId)
                    )
            );
        }

        ConversationDTO dto = toDTO(conversation);
        dto.setHiddenForCurrentUser(false);
        dto.setHiddenRequiresPin(false);
        dto.setClearBeforeAt(visibility != null ? visibility.getClearBeforeAt() : null);
        return dto;
    }

    public List<ConversationDTO> getGroupConversations() {
        return conversationRepository.findByIsGroupTrueOrderByLastMessageAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<ConversationDTO> getDirectConversations() {
        return conversationRepository.findByIsGroupFalseOrderByLastMessageAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ConversationDTO getOrCreateDirectConversation(String userId1, String userId2) {
        // Try to find existing conversation
        List<Conversation> conversations = conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(userId1);
        for (Conversation conv : conversations) {
            if (!conv.isGroup() && conv.getParticipantIds().contains(userId1) && conv.getParticipantIds().contains(userId2)) {
                HiddenConversation visibility = hiddenConversationRepository
                        .findByUserIdAndConversationId(userId1, conv.getId())
                        .orElse(null);
                // User explicitly starts this direct chat again -> always restore visibility for requester.
                // This prevents opening a blank/hidden thread from "New message" flow.
                if (visibility != null && visibility.isHidden()) {
                    visibility.setHidden(false);
                    visibility.setRequirePinUnlock(false);
                    visibility.setPinHash(null);
                    visibility.setClearBeforeAt(null);
                    visibility.setLastAccessAt(LocalDateTime.now());
                    visibility.setUpdatedAt(LocalDateTime.now());
                    hiddenConversationRepository.save(visibility);
                }

                ConversationDTO dto = toDTO(conv);
                applyUserVisibleLastMessage(dto, userId1, visibility != null ? visibility.getClearBeforeAt() : null);
                dto.setHiddenForCurrentUser(visibility != null && visibility.isHidden());
                dto.setHiddenRequiresPin(visibility != null && visibility.isHidden() && visibility.isRequirePinUnlock());
                        dto.setClearBeforeAt(visibility != null ? visibility.getClearBeforeAt() : null);
                return dto;
            }
        }
        
        // Create new conversation — check privacy first
        if (!commonServiceClientFacade.canMessage(userId1, userId2)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Người dùng này chỉ cho phép bạn bè nhắn tin");
        }

        // Create new conversation
        ConversationDTO newConv = new ConversationDTO();
        newConv.setParticipantIds(java.util.Arrays.asList(userId1, userId2));
        newConv.setGroup(false);
        return createConversation(newConv);
    }

    public ConversationDTO getConversationById(String id) {
        return conversationRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + id));
    }

    public ConversationDTO createConversation(ConversationDTO conversationDTO) {
        if (conversationDTO.isGroup()) {
            return createGroupConversation(conversationDTO);
        }

        // Direct chat validation: exactly 2 unique participants
        if (conversationDTO.getParticipantIds() == null || conversationDTO.getParticipantIds().size() < 2) {
            throw new IllegalArgumentException("Direct conversation requires exactly 2 participants");
        }

        List<String> distinct = conversationDTO.getParticipantIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (distinct.size() != 2) {
            throw new IllegalArgumentException("Direct conversation must have 2 distinct participants");
        }

        conversationDTO.setParticipantIds(distinct);
        conversationDTO.setGroup(false);

        Conversation conversation = toEntity(conversationDTO);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        return toDTO(saved);
    }

    public ConversationDTO updateConversation(String id, ConversationDTO conversationDTO) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + id));
        
        conversation.setGroupName(conversationDTO.getGroupName());
        conversation.setGroupAvatar(conversationDTO.getGroupAvatar());
        conversation.setLastMessagePreview(conversationDTO.getLastMessagePreview());
        conversation.setLastMessageAt(conversationDTO.getLastMessageAt());
        conversation.setUpdatedAt(LocalDateTime.now());
        
        Conversation updated = conversationRepository.save(conversation);
        return toDTO(updated);
    }

    public ConversationDTO updateConversationMeta(String conversationId, ConversationMetaUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        String oldGroupName = conversation.getGroupName();
        boolean oldApprovalsRequired = conversation.isApprovalsRequired();
        boolean oldOnlyAdminsCanSend = conversation.isOnlyAdminsCanSend();
        boolean oldOnlyAdminsCanAddMembers = conversation.isOnlyAdminsCanAddMembers();

        if (request.getRequesterId() == null || request.getRequesterId().isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }

        if (conversation.isGroup()) {
            ensureManager(conversation, request.getRequesterId());
        } else {
            if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(request.getRequesterId())) {
                throw new IllegalArgumentException("Requester is not a participant of this conversation");
            }
        }

        if (request.getGroupName() != null) {
            String name = request.getGroupName().trim();
            conversation.setGroupName(name.isBlank() ? conversation.getGroupName() : name);
        }
        if (request.getGroupAvatar() != null) {
            String avatar = request.getGroupAvatar().trim();
            conversation.setGroupAvatar(avatar.isBlank() ? conversation.getGroupAvatar() : avatar);
        }
        if (request.getDescription() != null) {
            conversation.setDescription(request.getDescription().trim());
        }
        if (request.getApprovalsRequired() != null && conversation.isGroup()) {
            conversation.setApprovalsRequired(request.getApprovalsRequired());
        }
        if (request.getOnlyAdminsCanSend() != null && conversation.isGroup()) {
            conversation.setOnlyAdminsCanSend(request.getOnlyAdminsCanSend());
        }
        if (request.getOnlyAdminsCanAddMembers() != null && conversation.isGroup()) {
            conversation.setOnlyAdminsCanAddMembers(request.getOnlyAdminsCanAddMembers());
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        if (conversation.isGroup()) {
            String actor = getParticipantDisplayName(conversation, request.getRequesterId());
                if (request.getGroupName() != null
                    && saved.getGroupName() != null
                    && !saved.getGroupName().equals(oldGroupName)) {
                emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.GROUP_RENAMED,
                        actor + " da doi ten nhom thanh \"" + saved.getGroupName() + "\"");
            }

            if (oldApprovalsRequired != saved.isApprovalsRequired()) {
                emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.JOIN_APPROVALS_UPDATED,
                        actor + (saved.isApprovalsRequired()
                                ? " da bat duyet thanh vien moi"
                                : " da tat duyet thanh vien moi"));
            }

            if (oldOnlyAdminsCanSend != saved.isOnlyAdminsCanSend()) {
                emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.SEND_PERMISSION_UPDATED,
                        actor + (saved.isOnlyAdminsCanSend()
                                ? " da bat che do chi admin duoc gui tin"
                                : " da tat che do chi admin duoc gui tin"));
            }

            if (oldOnlyAdminsCanAddMembers != saved.isOnlyAdminsCanAddMembers()) {
                emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.ADD_MEMBER_PERMISSION_UPDATED,
                        actor + (saved.isOnlyAdminsCanAddMembers()
                                ? " da bat che do chi admin duoc them thanh vien"
                                : " da tat che do chi admin duoc them thanh vien"));
            }

            // Emit CONVERSATION_META_UPDATED with new field values so all clients
            // patch their state in realtime without needing to call loadConversations()
            try {
                Map<String, Object> metaPayload = new java.util.HashMap<>();
                metaPayload.put("conversationId", saved.getId());
                metaPayload.put("onlyAdminsCanSend", saved.isOnlyAdminsCanSend());
                metaPayload.put("approvalsRequired", saved.isApprovalsRequired());
                metaPayload.put("groupName", saved.getGroupName());
                metaPayload.put("groupAvatar", saved.getGroupAvatar());
                SocketEventDTO metaEvent = new SocketEventDTO();
                metaEvent.setType(SocketEventTypes.CONVERSATION_META_UPDATED);
                metaEvent.setUserId(request.getRequesterId());
                metaEvent.setData(metaPayload);
                metaEvent.setTimestamp(java.time.LocalDateTime.now());
                for (String participantId : saved.getParticipantIds()) {
                    socketEmitterService.emitToUserById(participantId, metaEvent);
                }
            } catch (Exception e) {
                log.warn("Failed to emit CONVERSATION_META_UPDATED after meta update: {}", e.getMessage());
            }
        }

        return toDTO(saved);
    }

    public ConversationDTO leaveGroup(String conversationId, LeaveGroupRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot leave a direct conversation using this endpoint");
        }

        if (request.getRequesterId() == null || request.getRequesterId().isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }

        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(request.getRequesterId())) {
            throw new IllegalArgumentException("Requester is not a participant of this group");
        }

        boolean isOwner = conversation.getOwnerId() != null && conversation.getOwnerId().equals(request.getRequesterId());
        String actorName = getParticipantDisplayName(conversation, request.getRequesterId());
        if (isOwner) {
            String newOwnerId = request.getNewOwnerId();
            if (newOwnerId == null || newOwnerId.isBlank()) {
                throw new IllegalArgumentException("Owner must transfer ownership before leaving (newOwnerId is required)");
            }
            newOwnerId = newOwnerId.trim();
            if (newOwnerId.equals(request.getRequesterId())) {
                throw new IllegalArgumentException("newOwnerId must be different from requesterId");
            }
            if (!conversation.getParticipantIds().contains(newOwnerId)) {
                throw new IllegalArgumentException("New owner must be a participant");
            }
            conversation.setOwnerId(newOwnerId);
            // Ensure owner is not in admin list
            if (conversation.getAdminIds() != null) {
                conversation.getAdminIds().remove(newOwnerId);
            }

            String newOwnerName = resolveUserDisplayName(newOwnerId);
            emitGroupSystemEvent(conversation, request.getRequesterId(), SocketEventTypes.OWNER_TRANSFERRED,
                    actorName + " da chuyen quyen chu nhom cho " + newOwnerName);
        }

        Set<String> participants = new HashSet<>(conversation.getParticipantIds());
        participants.remove(request.getRequesterId());

        if (participants.size() < 3) {
            throw new IllegalStateException("Group must have at least 3 members. You cannot leave right now.");
        }

        conversation.setParticipantIds(new ArrayList<>(participants));
        if (conversation.getAdminIds() != null) {
            conversation.getAdminIds().remove(request.getRequesterId());
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.MEMBER_LEFT, actorName + " da roi nhom");

        return toDTO(saved);
    }

    public ConversationDTO requestToJoin(String conversationId, String requesterId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Join requests are only supported for group conversations");
        }
        if (!conversation.isApprovalsRequired()) {
            throw new IllegalStateException("This group does not require join approvals");
        }
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }
        if (conversation.getParticipantIds() != null && conversation.getParticipantIds().contains(requesterId)) {
            throw new IllegalStateException("Requester is already a member of this group");
        }

        List<String> pending = conversation.getPendingJoinIds();
        if (pending == null) {
            pending = new ArrayList<>();
        }
        if (!pending.contains(requesterId)) {
            pending.add(requesterId);
        }
        conversation.setPendingJoinIds(pending);
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        // Emit realtime notification to owner & admins
        try {
            SocketEventDTO event = new SocketEventDTO();
            event.setType(SocketEventTypes.JOIN_REQUEST_CREATED);
            event.setUserId(requesterId);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("conversationId", saved.getId());
            payload.put("requesterId", requesterId);
            event.setData(payload);
            event.setTimestamp(java.time.LocalDateTime.now());

            java.util.Set<String> targets = new java.util.HashSet<>();
            if (saved.getOwnerId() != null) {
                targets.add(saved.getOwnerId());
            }
            if (saved.getAdminIds() != null) {
                targets.addAll(saved.getAdminIds());
            }
            for (String targetId : targets) {
                socketEmitterService.emitToUserById(targetId, event);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to emit JOIN_REQUEST_CREATED event: {}", e.getMessage());
        }

        return toDTO(saved);
    }

    public List<String> getPendingJoinRequests(String conversationId, String requesterId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Join requests are only supported for group conversations");
        }
        ensureManager(conversation, requesterId);
        List<String> pending = conversation.getPendingJoinIds();
        return pending == null ? new ArrayList<>() : new ArrayList<>(pending);
    }

    public ConversationDTO handleJoinRequest(String conversationId, JoinRequestUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Join requests are only supported for group conversations");
        }
        ensureManager(conversation, request.getApproverId());

        if (request.getRequesterId() == null || request.getRequesterId().isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }

        List<String> pending = conversation.getPendingJoinIds();
        if (pending == null || !pending.remove(request.getRequesterId())) {
            throw new IllegalArgumentException("No pending join request for this user");
        }

        if (request.isApproved()) {
            List<String> participants = conversation.getParticipantIds();
            if (participants == null) {
                participants = new ArrayList<>();
            }
            if (!participants.contains(request.getRequesterId())) {
                participants.add(request.getRequesterId());
            }
            conversation.setParticipantIds(participants);
        }

        conversation.setPendingJoinIds(pending);
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        if (request.isApproved()) {
            String joinerName = resolveUserDisplayName(request.getRequesterId());
            emitGroupSystemEvent(saved, request.getApproverId(), SocketEventTypes.JOIN_REQUEST_APPROVED, joinerName + " da tham gia nhom");
        }

        // Notify requester about decision
        try {
            SocketEventDTO event = new SocketEventDTO();
            event.setType(SocketEventTypes.JOIN_REQUEST_UPDATED);
            event.setUserId(request.getRequesterId());
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("conversationId", saved.getId());
            payload.put("approved", request.isApproved());
            payload.put("approverId", request.getApproverId());
            event.setData(payload);
            event.setTimestamp(java.time.LocalDateTime.now());

            socketEmitterService.emitToUserById(request.getRequesterId(), event);
        } catch (Exception e) {
            log.warn("⚠️ Failed to emit JOIN_REQUEST_UPDATED event: {}", e.getMessage());
        }

        return toDTO(saved);
    }

    public void deleteConversation(String id, String requesterId) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + id));

        // For groups: only owner can delete. requesterId is required.
        if (conversation.isGroup()) {
            if (requesterId == null || requesterId.isBlank()) {
                throw new IllegalArgumentException("requesterId is required to delete a group conversation");
            }
            if (conversation.getOwnerId() == null || !conversation.getOwnerId().equals(requesterId)) {
                throw new IllegalArgumentException("Only the owner can delete a group conversation");
            }
            conversationRepository.deleteById(id);
            return;
        }

        // For direct chats: if requesterId provided, enforce membership. If not provided, allow (legacy).
        if (requesterId != null && !requesterId.isBlank()) {
            if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(requesterId)) {
                throw new IllegalArgumentException("Requester is not a participant of this conversation");
            }
        }
        conversationRepository.deleteById(id);
    }

    public ConversationDTO addMembers(String conversationId, GroupMemberUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot add members to a direct conversation");
        }

        // Any member of the group can invite friends.
        // If onlyAdminsCanAddMembers is ON, only managers may add directly —
        // but regular members can still invite (invitees go to pending if approvalsRequired is ON).
        boolean isManager = (conversation.getOwnerId() != null && conversation.getOwnerId().equals(request.getRequesterId()))
                || (conversation.getAdminIds() != null && conversation.getAdminIds().contains(request.getRequesterId()));

        ensureParticipant(conversation, request.getRequesterId());

        Set<String> newMembers = sanitizeIds(request.getParticipantIds());
        if (newMembers.isEmpty()) {
            throw new IllegalArgumentException("participantIds cannot be empty");
        }

        // Filter out banned users — cannot re-add banned members
        List<String> banned = conversation.getBannedUserIds();
        if (banned != null && !banned.isEmpty()) {
            newMembers.removeAll(banned);
            if (newMembers.isEmpty()) {
                throw new IllegalArgumentException("All specified users are banned from this group");
            }
        }

        // Filter out users who block group invites from non-friends
        String inviterId = request.getRequesterId();
        newMembers.removeIf(memberId -> !commonServiceClientFacade.canInviteGroup(inviterId, memberId));
        if (newMembers.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Tất cả người dùng đã chặn lời mời nhóm từ người lạ");
        }

        // Remove already-existing participants (no-op for them)
        Set<String> existing = new HashSet<>(conversation.getParticipantIds());
        newMembers.removeAll(existing);
        if (newMembers.isEmpty()) {
            return toDTO(conversation); // everyone already in group
        }

        // Non-managers: if approvals required OR onlyAdminsCanAddMembers → put in pending queue
        boolean routeToPending = !isManager && (conversation.isApprovalsRequired() || conversation.isOnlyAdminsCanAddMembers());

        if (routeToPending) {
            List<String> pending = conversation.getPendingJoinIds();
            if (pending == null) pending = new ArrayList<>();
            for (String memberId : newMembers) {
                if (!pending.contains(memberId)) {
                    pending.add(memberId);
                }
            }
            conversation.setPendingJoinIds(pending);
            conversation.setUpdatedAt(LocalDateTime.now());
            Conversation saved = conversationRepository.save(conversation);

            // Notify admins/owner of new pending requests
            try {
                SocketEventDTO event = new SocketEventDTO();
                event.setType(SocketEventTypes.JOIN_REQUEST_CREATED);
                event.setUserId(request.getRequesterId());
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("conversationId", saved.getId());
                payload.put("requesterId", request.getRequesterId());
                payload.put("pendingIds", new ArrayList<>(newMembers));
                event.setData(payload);
                event.setTimestamp(java.time.LocalDateTime.now());
                java.util.Set<String> targets = new java.util.HashSet<>();
                if (saved.getOwnerId() != null) targets.add(saved.getOwnerId());
                if (saved.getAdminIds() != null) targets.addAll(saved.getAdminIds());
                for (String targetId : targets) {
                    socketEmitterService.emitToUserById(targetId, event);
                }
            } catch (Exception e) {
                log.warn("Failed to emit JOIN_REQUEST_CREATED after member invite: {}", e.getMessage());
            }

            return toDTO(saved);
        }

        // Manager path (or no restrictions): add directly
        existing.addAll(newMembers);
        if (existing.size() < 3) {
            throw new IllegalStateException("Group must have at least 3 members");
        }

        conversation.setParticipantIds(new ArrayList<>(existing));
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        String actorName = getParticipantDisplayName(conversation, request.getRequesterId());
        List<String> addedNames = newMembers.stream().map(this::resolveUserDisplayName).toList();
        emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.MEMBERS_ADDED,
            actorName + " da them " + String.join(", ", addedNames) + " vao nhom");

        return toDTO(saved);
    }

    public ConversationDTO removeMember(String conversationId, RemoveMemberRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot remove members from a direct conversation");
        }

        if (request.getParticipantId() == null || request.getParticipantId().isBlank()) {
            throw new IllegalArgumentException("participantId is required");
        }

        boolean isOwner = conversation.getOwnerId() != null && conversation.getOwnerId().equals(request.getRequesterId());
        boolean isAdmin = conversation.getAdminIds() != null && conversation.getAdminIds().contains(request.getRequesterId());
        boolean selfRemove = request.getRequesterId() != null && request.getRequesterId().equals(request.getParticipantId());

        if (!isOwner && !isAdmin && !selfRemove) {
            throw new IllegalArgumentException("Requester is not allowed to remove this member");
        }

        if (conversation.getOwnerId() != null && conversation.getOwnerId().equals(request.getParticipantId())) {
            throw new IllegalArgumentException("Owner cannot be removed. Transfer ownership first.");
        }

        if (conversation.getAdminIds() != null
                && conversation.getAdminIds().contains(request.getParticipantId())
                && !isOwner
                && !selfRemove) {
            throw new IllegalArgumentException("Only owner can remove an admin");
        }

        Set<String> participants = new HashSet<>(conversation.getParticipantIds());
        if (!participants.remove(request.getParticipantId())) {
            throw new IllegalArgumentException("Member is not part of the group");
        }

        // If admin/owner removed themselves, clean from admin list
        if (conversation.getAdminIds() != null) {
            conversation.getAdminIds().remove(request.getParticipantId());
        }

        if (participants.size() < 3) {
            throw new IllegalStateException("Group must have at least 3 members. Add someone before removing this member.");
        }

        conversation.setParticipantIds(new ArrayList<>(participants));
        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        String actorName = getParticipantDisplayName(conversation, request.getRequesterId());
        String removedName = resolveUserDisplayName(request.getParticipantId());
        emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.MEMBER_REMOVED,
            actorName + " da xoa " + removedName + " khoi nhom");

        return toDTO(saved);
    }

    public ConversationDTO updateGroupRoles(String conversationId, GroupRoleUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));

        String oldOwnerId = conversation.getOwnerId();
        Set<String> oldAdmins = sanitizeIds(conversation.getAdminIds());

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Roles can only be updated for group conversations");
        }

        if (request.getRequesterId() == null || !request.getRequesterId().equals(conversation.getOwnerId())) {
            throw new IllegalArgumentException("Only the owner can update roles");
        }

        if (request.getNewOwnerId() != null && !request.getNewOwnerId().isBlank()) {
            if (!conversation.getParticipantIds().contains(request.getNewOwnerId())) {
                throw new IllegalArgumentException("New owner must be a participant");
            }
            conversation.setOwnerId(request.getNewOwnerId());
        }

        if (request.getAdminIds() != null) {
            Set<String> admins = sanitizeIds(request.getAdminIds());
            admins.remove(conversation.getOwnerId()); // owner implicitly has all permissions

            for (String adminId : admins) {
                if (!conversation.getParticipantIds().contains(adminId)) {
                    throw new IllegalArgumentException("Admin must be a participant: " + adminId);
                }
            }
            conversation.setAdminIds(new ArrayList<>(admins));
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        String actorName = getParticipantDisplayName(conversation, request.getRequesterId());
        if (oldOwnerId != null && !oldOwnerId.equals(saved.getOwnerId())) {
            emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.OWNER_TRANSFERRED,
                    actorName + " da chuyen quyen chu nhom cho " + resolveUserDisplayName(saved.getOwnerId()));
        }

        Set<String> newAdmins = sanitizeIds(saved.getAdminIds());
        Set<String> promoted = new HashSet<>(newAdmins);
        promoted.removeAll(oldAdmins);
        if (!promoted.isEmpty()) {
            emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.ADMINS_UPDATED,
                    actorName + " da bo nhiem admin: " + promoted.stream().map(this::resolveUserDisplayName).collect(Collectors.joining(", ")));
        }

        Set<String> demoted = new HashSet<>(oldAdmins);
        demoted.removeAll(newAdmins);
        if (!demoted.isEmpty()) {
            emitGroupSystemEvent(saved, request.getRequesterId(), SocketEventTypes.ADMINS_UPDATED,
                    actorName + " da go admin: " + demoted.stream().map(this::resolveUserDisplayName).collect(Collectors.joining(", ")));
        }

        // Broadcast updated adminIds + ownerId to all participants so clients update in realtime
        try {
            Map<String, Object> metaPayload = new java.util.HashMap<>();
            metaPayload.put("conversationId", saved.getId());
            metaPayload.put("ownerId", saved.getOwnerId());
            metaPayload.put("adminIds", saved.getAdminIds() != null ? saved.getAdminIds() : new ArrayList<>());
            SocketEventDTO metaEvent = new SocketEventDTO();
            metaEvent.setType(SocketEventTypes.CONVERSATION_META_UPDATED);
            metaEvent.setUserId(request.getRequesterId());
            metaEvent.setData(metaPayload);
            metaEvent.setTimestamp(java.time.LocalDateTime.now());
            for (String participantId : saved.getParticipantIds()) {
                socketEmitterService.emitToUserById(participantId, metaEvent);
            }
        } catch (Exception e) {
            log.warn("Failed to emit CONVERSATION_META_UPDATED after role update: {}", e.getMessage());
        }

        return toDTO(saved);
    }

    private ConversationDTO createGroupConversation(ConversationDTO conversationDTO) {
        Set<String> participantIds = sanitizeIds(conversationDTO.getParticipantIds());

        if (conversationDTO.getOwnerId() == null || conversationDTO.getOwnerId().isBlank()) {
            throw new IllegalArgumentException("ownerId is required for group conversation");
        }

        participantIds.add(conversationDTO.getOwnerId());

        if (participantIds.size() < 3) {
            throw new IllegalArgumentException("Group conversation requires at least 3 members (including owner)");
        }

        conversationDTO.setParticipantIds(new ArrayList<>(participantIds));
        conversationDTO.setGroup(true);

        // Admins must be subset of participants and cannot include owner
        Set<String> adminIds = sanitizeIds(conversationDTO.getAdminIds());
        adminIds.remove(conversationDTO.getOwnerId());
        for (String adminId : adminIds) {
            if (!participantIds.contains(adminId)) {
                throw new IllegalArgumentException("Admin must be a participant: " + adminId);
            }
        }
        conversationDTO.setAdminIds(new ArrayList<>(adminIds));

        if (conversationDTO.getGroupName() == null || conversationDTO.getGroupName().isBlank()) {
            // Auto-generate group name from first 3 participant display names
            List<String> names = participantIds.stream()
                    .limit(3)
                    .map(this::resolveUserDisplayName)
                    .filter(n -> n != null && !n.isBlank() && !n.equals("Unknown"))
                    .collect(Collectors.toList());
            if (names.isEmpty()) {
                conversationDTO.setGroupName("New Group Chat");
            } else {
                String autoName = String.join(", ", names);
                if (participantIds.size() > 3) {
                    autoName += "...";
                }
                conversationDTO.setGroupName(autoName);
            }
        }

        Conversation conversation = toEntity(conversationDTO);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        // Notify all members so they see the new group in their chat list in real-time.
        String ownerDisplayName = resolveUserDisplayName(conversationDTO.getOwnerId());
        String groupDisplayName = saved.getGroupName() != null ? saved.getGroupName() : "New Group Chat";
        emitGroupSystemEvent(
            saved,
            conversationDTO.getOwnerId(),
            SocketEventTypes.MEMBERS_ADDED,
            ownerDisplayName + " da tao nhom \"" + groupDisplayName + "\""
        );

        return toDTO(saved);
    }

    private void ensureManager(Conversation conversation, String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }
        boolean isOwner = conversation.getOwnerId() != null && conversation.getOwnerId().equals(requesterId);
        boolean isAdmin = conversation.getAdminIds() != null && conversation.getAdminIds().contains(requesterId);
        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Requester does not have permission to manage group members");
        }
    }

    private void ensureParticipant(Conversation conversation, String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }
        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(requesterId)) {
            throw new IllegalArgumentException("Requester is not a participant of this conversation");
        }
    }

    // ── Phase A Methods ─────────────────────────────────────────────────────

    private Conversation getConversationEntity(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Conversation not found"));
    }

    public ConversationDTO toggleMute(String conversationId, String userId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureParticipant(conversation, userId);

        List<String> muted = conversation.getMutedByUserIds();
        if (muted == null) {
            muted = new ArrayList<>();
        }
        if (muted.contains(userId)) {
            muted.remove(userId);
        } else {
            muted.add(userId);
        }
        conversation.setMutedByUserIds(muted);
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO togglePin(String conversationId, String userId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureParticipant(conversation, userId);

        List<String> pinned = conversation.getPinnedByUserIds();
        if (pinned == null) {
            pinned = new ArrayList<>();
        }
        if (pinned.contains(userId)) {
            pinned.remove(userId);
        } else {
            pinned.add(userId);
        }
        conversation.setPinnedByUserIds(pinned);
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO toggleBan(String conversationId, String requesterId, String targetUserId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureManager(conversation, requesterId);

        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        List<String> banned = conversation.getBannedUserIds();
        if (banned == null) {
            banned = new ArrayList<>();
        }

        if (banned.contains(targetUserId)) {
            banned.remove(targetUserId);
            emitGroupSystemEvent(conversation, requesterId, SocketEventTypes.MEMBERS_ADDED, 
                    getParticipantDisplayName(conversation, requesterId) + " da go ban cho " + resolveUserDisplayName(targetUserId));
        } else {
            banned.add(targetUserId);
            // If they are a member, remove them
            if (conversation.getParticipantIds() != null && conversation.getParticipantIds().contains(targetUserId)) {
                conversation.getParticipantIds().remove(targetUserId);
            }
            emitGroupSystemEvent(conversation, requesterId, SocketEventTypes.MEMBER_REMOVED, 
                    getParticipantDisplayName(conversation, requesterId) + " da ban " + resolveUserDisplayName(targetUserId));
        }
        conversation.setBannedUserIds(banned);
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO updateNickname(String conversationId, String requesterId, String nickname) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureParticipant(conversation, requesterId);

        java.util.Map<String, String> nicknames = conversation.getNicknames();
        if (nicknames == null) {
            nicknames = new java.util.HashMap<>();
        }

        if (nickname == null || nickname.isBlank()) {
            nicknames.remove(requesterId);
        } else {
            nicknames.put(requesterId, nickname);
        }
        conversation.setNicknames(nicknames);

        if (conversation.isGroup()) {
            emitGroupSystemEvent(conversation, requesterId, SocketEventTypes.CONVERSATION_META_UPDATED, 
                resolveUserDisplayName(requesterId) + " da doi biet danh thanh " + nickname);
        }

        return toDTO(conversationRepository.save(conversation));
    }

    // ── Phase B Methods ─────────────────────────────────────────────────────

    public String getInviteLink(String conversationId, String requesterId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureParticipant(conversation, requesterId);

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Invite links are only for groups");
        }

        if (conversation.getInviteLinkToken() == null || conversation.getInviteLinkToken().isBlank()) {
            conversation.setInviteLinkToken(java.util.UUID.randomUUID().toString());
            conversationRepository.save(conversation);
        }

        return conversation.getInviteLinkToken();
    }

    public String resetInviteLink(String conversationId, String requesterId) {
        Conversation conversation = getConversationEntity(conversationId);
        ensureManager(conversation, requesterId);

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Invite links are only for groups");
        }

        conversation.setInviteLinkToken(java.util.UUID.randomUUID().toString());
        conversationRepository.save(conversation);

        return conversation.getInviteLinkToken();
    }

    public ConversationDTO joinByInviteLink(String token, String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }

        Conversation conversation = conversationRepository.findByInviteLinkToken(token)
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Invalid or expired invite link"));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot join this type of conversation via link");
        }

        List<String> banned = conversation.getBannedUserIds();
        if (banned != null && banned.contains(requesterId)) {
            throw new IllegalArgumentException("You are banned from this group");
        }

        List<String> participants = conversation.getParticipantIds();
        if (participants == null) {
            participants = new ArrayList<>();
        }

        if (participants.contains(requesterId)) {
            return toDTO(conversation); // Already a member
        }

        if (conversation.isApprovalsRequired()) {
            List<String> pending = conversation.getPendingJoinIds();
            if (pending == null) pending = new ArrayList<>();
            if (!pending.contains(requesterId)) {
                pending.add(requesterId);
                conversation.setPendingJoinIds(pending);
                conversationRepository.save(conversation);
                // Can emit JOIN_REQUEST_CREATED here
                emitGroupSystemEvent(conversation, requesterId, SocketEventTypes.JOIN_REQUEST_CREATED, 
                        resolveUserDisplayName(requesterId) + " da yeu cau tham gia nhom");
            }
            return toDTO(conversation);
        }

        participants.add(requesterId);
        conversation.setParticipantIds(participants);
        Conversation saved = conversationRepository.save(conversation);

        emitGroupSystemEvent(saved, requesterId, SocketEventTypes.MEMBERS_ADDED, 
                resolveUserDisplayName(requesterId) + " da tham gia qua link moi");

        return toDTO(saved);
    }

    private Set<String> sanitizeIds(List<String> ids) {
        if (ids == null) return new HashSet<>();
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void emitGroupSystemEvent(Conversation conversation, String actorUserId, String action, String content) {
        if (conversation == null || !conversation.isGroup()) {
            return;
        }
        try {
            messageService.createSystemMessage(conversation.getId(), actorUserId, action, content);
        } catch (Exception e) {
            log.warn("Failed to create system message {} in conversation {}: {}", action, conversation.getId(), e.getMessage());
        }
    }

    private String getParticipantDisplayName(Conversation conversation, String userId) {
        if (conversation != null
                && conversation.getParticipantIds() != null
                && conversation.getParticipantNames() != null
                && userId != null) {
            int index = conversation.getParticipantIds().indexOf(userId);
            if (index >= 0 && index < conversation.getParticipantNames().size()) {
                String name = conversation.getParticipantNames().get(index);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        return resolveUserDisplayName(userId);
    }

    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Unknown";
        }
        try {
            UserDTO user = commonServiceClientFacade.getUserById(userId);
            if (user == null) {
                return userId;
            }
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
            return userId;
        } catch (Exception ex) {
            return userId;
        }
    }

    private void applyUserVisibleLastMessage(ConversationDTO dto, String userId, LocalDateTime clearCutoff) {
        if (dto == null || dto.getId() == null || userId == null || userId.isBlank()) {
            return;
        }

        Message latest = findLatestVisibleMessage(dto.getId(), userId, clearCutoff);
        if (latest == null) {
            dto.setLastMessagePreview("");
            dto.setLastMessageAt(null);
            return;
        }

        dto.setLastMessagePreview(buildLastMessagePreview(latest));
        dto.setLastMessageAt(latest.getCreatedAt());
    }

    private Message findLatestVisibleMessage(String conversationId, String userId, LocalDateTime clearCutoff) {
        for (int pageIndex = 0; pageIndex < PREVIEW_SCAN_MAX_PAGES; pageIndex++) {
            Pageable pageable = PageRequest.of(pageIndex, PREVIEW_SCAN_PAGE_SIZE);
            List<Message> batch = messageRepository
                    .findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageable);
            if (batch.isEmpty()) {
                return null;
            }

            for (Message message : batch) {
                if (message.getCreatedAt() == null) {
                    continue;
                }
                if (clearCutoff != null && !message.getCreatedAt().isAfter(clearCutoff)) {
                    continue;
                }
                List<String> hiddenForUserIds = message.getHiddenForUserIds();
                if (hiddenForUserIds != null && hiddenForUserIds.contains(userId)) {
                    continue;
                }
                return message;
            }

            if (batch.size() < PREVIEW_SCAN_PAGE_SIZE) {
                return null;
            }
        }

        return null;
    }

    public ConversationDTO toggleBlock(String conversationId, String requesterId) {
        Conversation conversation = getConversationEntity(conversationId);
        List<String> blockedBy = conversation.getBlockedByUserIds();
        if (blockedBy == null) {
            blockedBy = new ArrayList<>();
        }

        if (blockedBy.contains(requesterId)) {
            blockedBy.remove(requesterId);
        } else {
            blockedBy.add(requesterId);
        }
        conversation.setBlockedByUserIds(blockedBy);

        Conversation saved = conversationRepository.save(conversation);
        
        // Emit event (only to the one who blocked? or both? Usually just a state update)
        // For now, no system message for block.
        
        return toDTO(saved);
    }

    public ConversationDTO updateBackground(String conversationId, String backgroundUrl) {
        Conversation conversation = getConversationEntity(conversationId);
        conversation.setBackgroundUrl(backgroundUrl);
        conversation.setUpdatedAt(LocalDateTime.now());
        
        Conversation saved = conversationRepository.save(conversation);
        
        // Emit event
        emitGroupSystemEvent(saved, null, SocketEventTypes.CONVERSATION_META_UPDATED, "Hinh nen da duoc thay doi");
        
        return toDTO(saved);
    }

    private static String buildLastMessagePreview(Message message) {
        if (message == null) {
            return "";
        }

        if (TYPE_SYSTEM.equalsIgnoreCase(message.getMessageType())) {
            String content = message.getContent();
            return content == null ? "" : (content.length() > 80 ? content.substring(0, 80) + "..." : content);
        }
        if (TYPE_POLL.equalsIgnoreCase(message.getMessageType())) {
            String question = message.getPollQuestion() != null ? message.getPollQuestion() : message.getContent();
            if (question == null || question.isBlank()) {
                return "[Poll]";
            }
            String text = "[Poll] " + question;
            return text.length() > 80 ? text.substring(0, 80) + "..." : text;
        }

        String content = message.getContent();
        if (content != null && !content.trim().isEmpty()) {
            return content.length() > 80 ? content.substring(0, 80) + "..." : content;
        }
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            return "[Attachment]";
        }
        if (message.getReplyToMessageId() != null) {
            String p = message.getReplyToContentPreview();
            return "[Reply] " + (p != null ? p : "");
        }
        return "";
    }

    /**
     * Collect all participant IDs that need user-info resolution across all conversations,
     * then fetch them in a SINGLE batch HTTP call. Returns a userId → UserDTO map.
     */
    private Map<String, UserDTO> batchPrefetchUsers(List<Conversation> conversations) {
        Set<String> missingIds = new java.util.LinkedHashSet<>();
        for (Conversation conv : conversations) {
            List<String> ids = conv.getParticipantIds();
            List<String> names = conv.getParticipantNames();
            List<String> avatars = conv.getParticipantAvatars();
            if (ids == null) continue;
            for (int i = 0; i < ids.size(); i++) {
                String existingName = (names != null && i < names.size()) ? names.get(i) : null;
                String existingAvatar = (avatars != null && i < avatars.size()) ? avatars.get(i) : null;
                if (existingName == null || existingName.isBlank() || existingAvatar == null) {
                    missingIds.add(ids.get(i));
                }
            }
        }

        if (missingIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        try {
            List<UserDTO> users = commonServiceClientFacade.batchLookup(new ArrayList<>(missingIds));
            Map<String, UserDTO> map = new java.util.HashMap<>();
            if (users != null) {
                for (UserDTO u : users) {
                    if (u != null && u.getId() != null) {
                        map.put(u.getId(), u);
                    }
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("⚠️ Batch user prefetch failed: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }

    /**
     * Same as toDTO but uses a pre-fetched user cache instead of calling getUserById per participant.
     */
    private ConversationDTO toDTOWithCache(Conversation conversation, Map<String, UserDTO> userCache) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setParticipantIds(conversation.getParticipantIds());

        List<String> participantIds = conversation.getParticipantIds() != null ? conversation.getParticipantIds() : List.of();
        List<String> participantNames = new ArrayList<>(conversation.getParticipantNames() != null ? conversation.getParticipantNames() : List.of());
        List<String> participantAvatars = new ArrayList<>(conversation.getParticipantAvatars() != null ? conversation.getParticipantAvatars() : List.of());

        while (participantNames.size() < participantIds.size()) participantNames.add(null);
        while (participantAvatars.size() < participantIds.size()) participantAvatars.add(null);
        if (participantNames.size() > participantIds.size())
            participantNames = new ArrayList<>(participantNames.subList(0, participantIds.size()));
        if (participantAvatars.size() > participantIds.size())
            participantAvatars = new ArrayList<>(participantAvatars.subList(0, participantIds.size()));

        for (int i = 0; i < participantIds.size(); i++) {
            String existingName = participantNames.get(i);
            String existingAvatar = participantAvatars.get(i);
            if (existingName != null && !existingName.isBlank() && existingAvatar != null) {
                continue;
            }

            String participantId = participantIds.get(i);
            UserDTO user = userCache.get(participantId);
            if (user != null) {
                if (existingName == null || existingName.isBlank()) {
                    String resolvedName = user.getFullName() != null && !user.getFullName().isBlank()
                            ? user.getFullName()
                            : user.getUsername();
                    participantNames.set(i, (resolvedName != null && !resolvedName.isBlank()) ? resolvedName : "Unknown User");
                }
                if (existingAvatar == null) {
                    participantAvatars.set(i, user.getAvatar());
                }
            } else if (existingName == null || existingName.isBlank()) {
                participantNames.set(i, "Unknown User");
            }
        }

        dto.setParticipantNames(participantNames);
        dto.setParticipantAvatars(participantAvatars);
        dto.setGroup(conversation.isGroup());
        dto.setGroupName(conversation.getGroupName());
        dto.setGroupAvatar(conversation.getGroupAvatar());
        dto.setDescription(conversation.getDescription());
        dto.setOnlyAdminsCanSend(conversation.isOnlyAdminsCanSend());
        dto.setOnlyAdminsCanAddMembers(conversation.isOnlyAdminsCanAddMembers());
        dto.setOwnerId(conversation.getOwnerId());
        dto.setAdminIds(conversation.getAdminIds());
        dto.setApprovalsRequired(conversation.isApprovalsRequired());
        dto.setPendingJoinIds(conversation.getPendingJoinIds());

        dto.setMutedByUserIds(conversation.getMutedByUserIds());
        dto.setPinnedByUserIds(conversation.getPinnedByUserIds());
        dto.setBannedUserIds(conversation.getBannedUserIds());
        dto.setNicknames(conversation.getNicknames());
        dto.setInviteLinkToken(conversation.getInviteLinkToken());
        dto.setBlockedByUserIds(conversation.getBlockedByUserIds());
        dto.setBackgroundUrl(conversation.getBackgroundUrl());
        dto.setAiAssistantEnabled(conversation.isAiAssistantEnabled());

        dto.setLastMessagePreview(conversation.getLastMessagePreview());
        dto.setLastMessageAt(conversation.getLastMessageAt());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());
        return dto;
    }

    private ConversationDTO toDTO(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setParticipantIds(conversation.getParticipantIds());

        List<String> participantIds = conversation.getParticipantIds() != null ? conversation.getParticipantIds() : List.of();
        List<String> participantNames = new ArrayList<>(conversation.getParticipantNames() != null ? conversation.getParticipantNames() : List.of());
        List<String> participantAvatars = new ArrayList<>(conversation.getParticipantAvatars() != null ? conversation.getParticipantAvatars() : List.of());

        // Keep list sizes aligned with participantIds. Fetch user info only for missing slots.
        while (participantNames.size() < participantIds.size()) {
            participantNames.add(null);
        }
        while (participantAvatars.size() < participantIds.size()) {
            participantAvatars.add(null);
        }
        if (participantNames.size() > participantIds.size()) {
            participantNames = new ArrayList<>(participantNames.subList(0, participantIds.size()));
        }
        if (participantAvatars.size() > participantIds.size()) {
            participantAvatars = new ArrayList<>(participantAvatars.subList(0, participantIds.size()));
        }

        for (int i = 0; i < participantIds.size(); i++) {
            String existingName = participantNames.get(i);
            String existingAvatar = participantAvatars.get(i);
            if (existingName != null && !existingName.isBlank() && existingAvatar != null) {
                continue;
            }

            String participantId = participantIds.get(i);
            try {
                UserDTO user = commonServiceClientFacade.getUserById(participantId);
                if (user != null) {
                    if (existingName == null || existingName.isBlank()) {
                        String resolvedName = user.getFullName() != null && !user.getFullName().isBlank()
                                ? user.getFullName()
                                : user.getUsername();
                        participantNames.set(i, (resolvedName != null && !resolvedName.isBlank()) ? resolvedName : "Unknown User");
                    }
                    if (existingAvatar == null) {
                        participantAvatars.set(i, user.getAvatar());
                    }
                } else if (existingName == null || existingName.isBlank()) {
                    participantNames.set(i, "Unknown User");
                }
            } catch (Exception e) {
                if (existingName == null || existingName.isBlank()) {
                    participantNames.set(i, "Unknown User");
                }
            }
        }

        dto.setParticipantNames(participantNames);
        dto.setParticipantAvatars(participantAvatars);
        dto.setGroup(conversation.isGroup());
        dto.setGroupName(conversation.getGroupName());
        dto.setGroupAvatar(conversation.getGroupAvatar());
        dto.setDescription(conversation.getDescription());
        dto.setOnlyAdminsCanSend(conversation.isOnlyAdminsCanSend());
        dto.setOnlyAdminsCanAddMembers(conversation.isOnlyAdminsCanAddMembers());
        dto.setOwnerId(conversation.getOwnerId());
        dto.setAdminIds(conversation.getAdminIds());
        dto.setApprovalsRequired(conversation.isApprovalsRequired());
        dto.setPendingJoinIds(conversation.getPendingJoinIds());
        
        // ── Phase A+B Maps ──
        dto.setMutedByUserIds(conversation.getMutedByUserIds());
        dto.setPinnedByUserIds(conversation.getPinnedByUserIds());
        dto.setBannedUserIds(conversation.getBannedUserIds());
        dto.setNicknames(conversation.getNicknames());
        dto.setInviteLinkToken(conversation.getInviteLinkToken());
        dto.setBlockedByUserIds(conversation.getBlockedByUserIds());
        dto.setBackgroundUrl(conversation.getBackgroundUrl());
        dto.setAiAssistantEnabled(conversation.isAiAssistantEnabled());

        dto.setLastMessagePreview(conversation.getLastMessagePreview());
        dto.setLastMessageAt(conversation.getLastMessageAt());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());
        return dto;
    }

    private Conversation toEntity(ConversationDTO dto) {
        Conversation conversation = new Conversation();
        conversation.setParticipantIds(dto.getParticipantIds());
        conversation.setParticipantNames(dto.getParticipantNames());
        conversation.setParticipantAvatars(dto.getParticipantAvatars());
        conversation.setGroup(dto.isGroup());
        conversation.setGroupName(dto.getGroupName());
        conversation.setGroupAvatar(dto.getGroupAvatar());
        conversation.setDescription(dto.getDescription());
        conversation.setOnlyAdminsCanSend(dto.isOnlyAdminsCanSend());
        conversation.setOnlyAdminsCanAddMembers(dto.isOnlyAdminsCanAddMembers());
        conversation.setOwnerId(dto.getOwnerId());
        conversation.setAdminIds(dto.getAdminIds());
        conversation.setApprovalsRequired(dto.isApprovalsRequired());
        conversation.setPendingJoinIds(dto.getPendingJoinIds());

        // ── Phase A+B Maps ──
        conversation.setMutedByUserIds(dto.getMutedByUserIds());
        conversation.setPinnedByUserIds(dto.getPinnedByUserIds());
        conversation.setBannedUserIds(dto.getBannedUserIds());
        conversation.setNicknames(dto.getNicknames());
        conversation.setInviteLinkToken(dto.getInviteLinkToken());
        conversation.setBlockedByUserIds(dto.getBlockedByUserIds());
        conversation.setBackgroundUrl(dto.getBackgroundUrl());
        conversation.setAiAssistantEnabled(dto.getAiAssistantEnabled() != null && dto.getAiAssistantEnabled());
        return conversation;
    }
}
