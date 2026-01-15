package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.ConversationDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

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
        Conversation conversation = toEntity(conversationDTO);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
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

    public void deleteConversation(String id) {
        conversationRepository.deleteById(id);
    }

    private ConversationDTO toDTO(Conversation conversation) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setParticipantIds(conversation.getParticipantIds());
        dto.setParticipantNames(conversation.getParticipantNames());
        dto.setParticipantAvatars(conversation.getParticipantAvatars());
        dto.setGroup(conversation.isGroup());
        dto.setGroupName(conversation.getGroupName());
        dto.setGroupAvatar(conversation.getGroupAvatar());
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
        return conversation;
    }
}

