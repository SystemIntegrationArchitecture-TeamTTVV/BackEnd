package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.dto.ConversationMetaUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupMemberUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.GroupRoleUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.JoinRequestUpdateRequest;
import edu.iuh.fit.se.messegeservice.dto.LeaveGroupRequest;
import edu.iuh.fit.se.messegeservice.dto.RemoveMemberRequest;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.dto.UserDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final RestTemplate restTemplate;
    private final SocketEmitterService socketEmitterService;
    
    @Value("${common.service.url:http://localhost:8081}")
    private String commonServiceUrl;

    public List<ConversationDTO> getConversationsByUserId(String userId) {
        return conversationRepository.findByParticipantIdsContainingOrderByLastMessageAtDesc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
                return toDTO(conv);
            }
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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + id));
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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + id));
        
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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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
        if (request.getApprovalsRequired() != null && conversation.isGroup()) {
            conversation.setApprovalsRequired(request.getApprovalsRequired());
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO leaveGroup(String conversationId, LeaveGroupRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO requestToJoin(String conversationId, String requesterId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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
            event.setType("JOIN_REQUEST_CREATED");
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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Join requests are only supported for group conversations");
        }
        ensureManager(conversation, requesterId);
        List<String> pending = conversation.getPendingJoinIds();
        return pending == null ? new ArrayList<>() : new ArrayList<>(pending);
    }

    public ConversationDTO handleJoinRequest(String conversationId, JoinRequestUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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

        // Notify requester about decision
        try {
            SocketEventDTO event = new SocketEventDTO();
            event.setType("JOIN_REQUEST_UPDATED");
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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + id));

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
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot add members to a direct conversation");
        }
        ensureManager(conversation, request.getRequesterId());

        Set<String> newMembers = sanitizeIds(request.getParticipantIds());
        if (newMembers.isEmpty()) {
            throw new IllegalArgumentException("participantIds cannot be empty");
        }

        Set<String> participants = new HashSet<>(conversation.getParticipantIds());
        participants.addAll(newMembers);

        if (participants.size() < 3) {
            throw new IllegalStateException("Group must have at least 3 members");
        }

        conversation.setParticipantIds(new ArrayList<>(participants));
        conversation.setUpdatedAt(LocalDateTime.now());
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO removeMember(String conversationId, RemoveMemberRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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
        return toDTO(conversationRepository.save(conversation));
    }

    public ConversationDTO updateGroupRoles(String conversationId, GroupRoleUpdateRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));

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
        return toDTO(conversationRepository.save(conversation));
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
            conversationDTO.setGroupName("New Group Chat");
        }

        Conversation conversation = toEntity(conversationDTO);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);
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

    private Set<String> sanitizeIds(List<String> ids) {
        if (ids == null) return new HashSet<>();
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private ConversationDTO toDTO(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setParticipantIds(conversation.getParticipantIds());
        
        // 🔥 Populate participant names and avatars from CommonService
        List<String> participantNames = new ArrayList<>();
        List<String> participantAvatars = new ArrayList<>();
        
        for (String participantId : conversation.getParticipantIds()) {
            try {
                String url = commonServiceUrl + "/api/users/" + participantId;
                UserDTO user = restTemplate.getForObject(url, UserDTO.class);
                if (user != null) {
                    participantNames.add(user.getFullName() != null ? user.getFullName() : user.getUsername());
                    participantAvatars.add(user.getAvatar());
                } else {
                    participantNames.add("Unknown User");
                    participantAvatars.add(null);
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to fetch user info for {}: {}", participantId, e.getMessage());
                participantNames.add("Unknown User");
                participantAvatars.add(null);
            }
        }
        
        dto.setParticipantNames(participantNames);
        dto.setParticipantAvatars(participantAvatars);
        dto.setGroup(conversation.isGroup());
        dto.setGroupName(conversation.getGroupName());
        dto.setGroupAvatar(conversation.getGroupAvatar());
        dto.setOwnerId(conversation.getOwnerId());
        dto.setAdminIds(conversation.getAdminIds());
        dto.setApprovalsRequired(conversation.isApprovalsRequired());
        dto.setPendingJoinIds(conversation.getPendingJoinIds());
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
        conversation.setOwnerId(dto.getOwnerId());
        conversation.setAdminIds(dto.getAdminIds());
        conversation.setApprovalsRequired(dto.isApprovalsRequired());
        conversation.setPendingJoinIds(dto.getPendingJoinIds());
        return conversation;
    }
}

