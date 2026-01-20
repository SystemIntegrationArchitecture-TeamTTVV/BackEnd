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
            SocketEventDTO socketEvent = SocketEventDTO.messageReceived(
                messageDTO.getSenderId(), 
                savedDTO
            );
            
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
            socketEmitterService.emitToAll(socketEvent);
            
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event for new message: {}", e.getMessage());
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
        return message;
    }
}

