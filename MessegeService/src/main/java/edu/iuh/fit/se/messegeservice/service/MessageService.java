package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SocketEmitterService socketEmitterService;

    public List<MessageDTO> getMessagesByConversationId(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(msg -> !msg.isDeleted())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getMessagesBySenderId(String senderId) {
        return messageRepository.findBySenderIdOrderByCreatedAtDesc(senderId).stream()
                .filter(msg -> !msg.isDeleted())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long getMessageCountByConversationId(String conversationId) {
        return messageRepository.countByConversationId(conversationId);
    }

    public MessageDTO getMessageById(String id) {
        return messageRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
    }

    public MessageDTO createMessage(MessageDTO messageDTO) {
        log.info("📥 Creating message: conversationId={}, senderId={}, content={}", 
            messageDTO.getConversationId(), messageDTO.getSenderId(), 
            messageDTO.getContent() != null ? messageDTO.getContent().substring(0, Math.min(50, messageDTO.getContent().length())) : "null");
        
        // Validate required fields
        if (messageDTO.getConversationId() == null || messageDTO.getConversationId().isEmpty()) {
            log.error("❌ conversationId is null or empty");
            throw new IllegalArgumentException("conversationId is required");
        }
        if (messageDTO.getSenderId() == null || messageDTO.getSenderId().isEmpty()) {
            log.error("❌ senderId is null or empty");
            throw new IllegalArgumentException("senderId is required");
        }
        
        // Validate membership
        Conversation conversation = conversationRepository.findById(messageDTO.getConversationId())
                .orElseThrow(() -> {
                    log.error("❌ Conversation not found: {}", messageDTO.getConversationId());
                    return new RuntimeException("Conversation not found: " + messageDTO.getConversationId());
                });

        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(messageDTO.getSenderId())) {
            throw new IllegalArgumentException("Sender is not a participant of this conversation");
        }

        Message message = toEntity(messageDTO, conversation);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        message.setDeleted(false);
        message.setEdited(false);
        
        log.info("💾 Before save - conversationId: {}, conversation: {}", 
            message.getConversationId(), 
            message.getConversation() != null ? message.getConversation().getId() : "null");
        
        Message saved = messageRepository.save(message);
        
        log.info("✅ After save - id: {}, conversationId: {}, conversation: {}", 
            saved.getId(),
            saved.getConversationId(), 
            saved.getConversation() != null ? saved.getConversation().getId() : "null");
        
        log.info("✅ Message saved with id: {}", saved.getId());
        
        // Update conversation last message
        conversation.setLastMessagePreview(messageDTO.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        
        MessageDTO savedDTO = toDTO(saved);
        
        log.info("📤 DTO after conversion - id: {}, conversationId: {}", 
            savedDTO.getId(), savedDTO.getConversationId());
        
        // 🚀 Emit socket event to all participants via /topic/public
        // Frontend will receive and filter based on conversationId and senderId
        try {
            // Removed: Old code that broadcasted to all users (security issue)
            // SocketEventDTO socketEvent = SocketEventDTO.messageReceived(...);
            
            log.info("� Socket event data before emit - MessageDTO: id={}, conversationId={}, senderId={}, content={}", 
                savedDTO.getId(), 
                savedDTO.getConversationId(), 
                savedDTO.getSenderId(), 
                savedDTO.getContent());
            
            log.info("�📨 Broadcasting MESSAGE_RECEIVED event for conversation: {} via /topic/public", 
                messageDTO.getConversationId());
            
            // Emit to all users via /topic/public
            // Frontend subscribers will filter messages based on:
            // 1. conversationId (only show in relevant conversation)
            // 2. senderId (avoid duplicate for sender)
            // 🔒 SECURITY FIX: Emit socket event ONLY to conversation participants
            // This prevents other users from seeing private conversations
            List<String> participantIds = conversation.getParticipantIds();
            if (participantIds != null && !participantIds.isEmpty()) {
                log.info("📨 Emitting MESSAGE_RECEIVED event to {} participants of conversation: {} (sender: {})", 
                    participantIds.size(), 
                    messageDTO.getConversationId(),
                    messageDTO.getSenderId());
                
                int emittedCount = 0;
                // Emit to each participant (except the sender, who already sent the message)
                for (String participantId : participantIds) {
                    if (!participantId.equals(messageDTO.getSenderId())) {
                        try {
                            SocketEventDTO participantEvent = SocketEventDTO.messageReceived(
                                participantId, // Target recipient
                                savedDTO
                            );
                            socketEmitterService.emitToUserById(participantId, participantEvent);
                            emittedCount++;
                            log.debug("✅ Emitted MESSAGE_RECEIVED to participant: {}", participantId);
                        } catch (Exception e) {
                            log.error("❌ Failed to emit to participant {}: {}", participantId, e.getMessage());
                            // Continue with other participants
                        }
                    }
                }
                
                log.info("✅ Successfully emitted MESSAGE_RECEIVED to {}/{} participants", 
                    emittedCount, participantIds.size() - 1);
            } else {
                log.warn("⚠️ Conversation has no participants, skipping socket emit");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event for new message: {}", e.getMessage(), e);
            // Don't fail the entire operation if socket emit fails
        }
        
        return savedDTO;
    }

    public MessageDTO updateMessage(String id, MessageDTO messageDTO) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
        
        message.setContent(messageDTO.getContent());
        message.setAttachments(messageDTO.getAttachments());
        message.setEdited(true);
        message.setUpdatedAt(LocalDateTime.now());
        
        Message updated = messageRepository.save(message);
        return toDTO(updated);
    }

    public MessageDTO togglePin(String id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
        message.setPinned(!message.isPinned());
        message.setUpdatedAt(LocalDateTime.now());
        return toDTO(messageRepository.save(message));
    }

    public MessageDTO toggleStar(String id, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required to star a message");
        }
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));

        List<String> starredBy = message.getStarredByUserIds();
        if (starredBy == null) {
            starredBy = new java.util.ArrayList<>();
        }
        if (starredBy.contains(userId)) {
            starredBy.remove(userId);
        } else {
            starredBy.add(userId);
        }
        message.setStarredByUserIds(starredBy);
        message.setUpdatedAt(LocalDateTime.now());
        return toDTO(messageRepository.save(message));
    }

    public MessageDTO toggleReaction(String id, String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("emoji is required");
        }
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));

        List<String> emojis = message.getEmojis();
        if (emojis == null) {
            emojis = new java.util.ArrayList<>();
        }
        if (emojis.contains(emoji)) {
            emojis.remove(emoji);
        } else {
            emojis.add(emoji);
        }
        message.setEmojis(emojis);
        message.setUpdatedAt(LocalDateTime.now());
        return toDTO(messageRepository.save(message));
    }

    public void deleteMessage(String id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
        message.setDeleted(true);
        messageRepository.save(message);
    }

    private MessageDTO toDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        
        // 🔥 Get conversationId from either field or DBRef
        String conversationId = message.getConversationId();
        if (conversationId == null && message.getConversation() != null) {
            conversationId = message.getConversation().getId();
        }
        dto.setConversationId(conversationId);
        
        dto.setSenderId(message.getSenderId());
        dto.setSenderName(message.getSenderName());
        dto.setSenderAvatar(message.getSenderAvatar());
        dto.setContent(message.getContent());
        dto.setEmojis(message.getEmojis());
        dto.setAttachments(message.getAttachments());
        dto.setPinned(message.isPinned());
        dto.setStarredByUserIds(message.getStarredByUserIds());
        dto.setDeleted(message.isDeleted());
        dto.setEdited(message.isEdited());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setUpdatedAt(message.getUpdatedAt());
        return dto;
    }

    private Message toEntity(MessageDTO dto, Conversation conversation) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setConversationId(conversation.getId()); // 🔥 Set conversationId explicitly
        message.setSenderId(dto.getSenderId());
        message.setSenderName(dto.getSenderName());
        message.setSenderAvatar(dto.getSenderAvatar());
        message.setContent(dto.getContent());
        message.setEmojis(dto.getEmojis());
        message.setAttachments(dto.getAttachments());
        message.setPinned(dto.getPinned() != null && dto.getPinned());
        message.setStarredByUserIds(dto.getStarredByUserIds());
        return message;
    }
}

