package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.GroupInviteDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.GroupInvite;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.GroupInviteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteService {

    private final GroupInviteRepository groupInviteRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final SocketEmitterService socketEmitterService;
    private final MessageService messageService;

    public GroupInviteDTO sendInvite(GroupInviteDTO request) {
        if (request.getConversationId() == null || request.getInviterId() == null || request.getInviteeId() == null) {
            throw new IllegalArgumentException("conversationId, inviterId, and inviteeId are required");
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Conversation not found"));
                
        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Cannot invite to a 1-1 conversation");
        }
        
        List<String> banned = conversation.getBannedUserIds();
        if (banned != null && banned.contains(request.getInviteeId())) {
            throw new IllegalArgumentException("User is banned from this group");
        }

        if (conversation.getParticipantIds() != null && conversation.getParticipantIds().contains(request.getInviteeId())) {
            throw new IllegalArgumentException("User is already in the group");
        }

        // Logic to clear old invites
        GroupInvite existing = groupInviteRepository.findByConversationIdAndInviteeId(
                request.getConversationId(), request.getInviteeId()).orElse(null);
                
        if (existing != null) {
            if ("PENDING".equals(existing.getStatus())) {
                throw new IllegalArgumentException("An invite is already pending");
            }
            // Update to pending if it was declined before
            existing.setStatus("PENDING");
            existing.setInviterId(request.getInviterId());
            existing.setUpdatedAt(LocalDateTime.now());
            GroupInvite saved = groupInviteRepository.save(existing);
            return toDTO(saved);
        }

        GroupInvite invite = new GroupInvite();
        invite.setConversationId(request.getConversationId());
        invite.setInviterId(request.getInviterId());
        invite.setInviteeId(request.getInviteeId());
        invite.setStatus("PENDING");
        invite.setCreatedAt(LocalDateTime.now());
        invite.setUpdatedAt(LocalDateTime.now());

        GroupInvite saved = groupInviteRepository.save(invite);
        return toDTO(saved);
    }

    public List<GroupInviteDTO> getInvitesForUser(String userId) {
        return groupInviteRepository.findByInviteeIdAndStatus(userId, "PENDING")
                .stream()
                .map(this::toDTOAndEnrich)
                .collect(Collectors.toList());
    }

    public GroupInviteDTO acceptInvite(String inviteId, String userId) {
        GroupInvite invite = groupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Invite not found"));

        if (!invite.getInviteeId().equals(userId)) {
            throw new IllegalArgumentException("You are not the invitee");
        }

        if (!"PENDING".equals(invite.getStatus())) {
            throw new IllegalArgumentException("Invite is not pending");
        }

        Conversation conversation = conversationRepository.findById(invite.getConversationId())
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Conversation not found"));

        List<String> participants = conversation.getParticipantIds();
        if (participants == null) participants = new ArrayList<>();
        
        if (!participants.contains(invite.getInviteeId())) {
            participants.add(invite.getInviteeId());
            conversation.setParticipantIds(participants);
            conversationRepository.save(conversation);
            
            // Generate system message and socket emit
            try {
                messageService.createSystemMessage(conversation.getId(), invite.getInviteeId(), SocketEventTypes.MEMBERS_ADDED, 
                        "Da tham gia nhom tu loi moi");
            } catch (Exception e) {
                log.warn("Could not create system message for invite accept: {}", e.getMessage());
            }
        }

        invite.setStatus("ACCEPTED");
        invite.setUpdatedAt(LocalDateTime.now());
        return toDTO(groupInviteRepository.save(invite));
    }

    public GroupInviteDTO declineInvite(String inviteId, String userId) {
        GroupInvite invite = groupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException("Invite not found"));

        if (!invite.getInviteeId().equals(userId)) {
            throw new IllegalArgumentException("You are not the invitee");
        }

        if (!"PENDING".equals(invite.getStatus())) {
            throw new IllegalArgumentException("Invite is not pending");
        }

        invite.setStatus("DECLINED");
        invite.setUpdatedAt(LocalDateTime.now());
        return toDTO(groupInviteRepository.save(invite));
    }

    private GroupInviteDTO toDTO(GroupInvite invite) {
        GroupInviteDTO dto = new GroupInviteDTO();
        dto.setId(invite.getId());
        dto.setConversationId(invite.getConversationId());
        dto.setInviterId(invite.getInviterId());
        dto.setInviteeId(invite.getInviteeId());
        dto.setStatus(invite.getStatus());
        dto.setCreatedAt(invite.getCreatedAt());
        dto.setUpdatedAt(invite.getUpdatedAt());
        return dto;
    }

    private GroupInviteDTO toDTOAndEnrich(GroupInvite invite) {
        GroupInviteDTO dto = toDTO(invite);
        try {
            dto.setConversation(conversationService.getConversationById(invite.getConversationId()));
        } catch (Exception e) {
            log.warn("Could not enrich conversation for invite {}: {}", invite.getId(), e.getMessage());
        }
        return dto;
    }
}
