package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.dto.CreateScheduledMessageRequest;
import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.dto.ScheduledMessageDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.ScheduledMessage;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.ScheduledMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledMessageService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final ScheduledMessageRepository scheduledMessageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;

    /**
     * Create a scheduled message.
     */
    public ScheduledMessageDTO createScheduledMessage(String conversationId, CreateScheduledMessageRequest request) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (request.getSenderId() == null || request.getSenderId().isBlank()) {
            throw new IllegalArgumentException("senderId is required");
        }
        if (request.getContent() == null || request.getContent().trim().isBlank()) {
            if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
                throw new IllegalArgumentException("content or attachments are required");
            }
        }
        if (request.getScheduledAt() == null) {
            throw new IllegalArgumentException("scheduledAt is required");
        }
        if (request.getScheduledAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("scheduledAt must be in the future");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(request.getSenderId())) {
            throw new IllegalArgumentException("Sender is not a participant of this conversation");
        }

        ScheduledMessage scheduled = new ScheduledMessage();
        scheduled.setConversationId(conversationId);
        scheduled.setSenderId(request.getSenderId());
        scheduled.setSenderName(request.getSenderName());
        scheduled.setSenderAvatar(request.getSenderAvatar());
        scheduled.setContent(request.getContent() != null ? request.getContent().trim() : "");
        scheduled.setAttachments(request.getAttachments());
        scheduled.setScheduledAt(request.getScheduledAt());
        scheduled.setStatus(STATUS_PENDING);
        scheduled.setCreatedAt(LocalDateTime.now());
        scheduled.setUpdatedAt(LocalDateTime.now());

        ScheduledMessage saved = scheduledMessageRepository.save(scheduled);
        log.info("📅 Scheduled message created: id={}, conversationId={}, scheduledAt={}",
                saved.getId(), conversationId, saved.getScheduledAt());

        return toDTO(saved);
    }

    /**
     * Get all scheduled messages (PENDING only) for a conversation by the requesting user.
     */
    public List<ScheduledMessageDTO> getScheduledMessages(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("User is not a participant of this conversation");
        }

        return scheduledMessageRepository
                .findByConversationIdAndSenderIdAndStatusOrderByScheduledAtAsc(conversationId, userId, STATUS_PENDING)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Cancel a scheduled message. Only the sender can cancel.
     */
    public void cancelScheduledMessage(String id, String userId) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        ScheduledMessage scheduled = scheduledMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled message not found: " + id));

        if (!scheduled.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Only the sender can cancel a scheduled message");
        }
        if (!STATUS_PENDING.equals(scheduled.getStatus())) {
            throw new IllegalStateException("Only PENDING scheduled messages can be cancelled");
        }

        scheduled.setStatus(STATUS_CANCELLED);
        scheduled.setUpdatedAt(LocalDateTime.now());
        scheduledMessageRepository.save(scheduled);

        log.info("🚫 Scheduled message cancelled: id={}", id);
    }

    /**
     * Runs every 30 seconds to check for scheduled messages that are due.
     */
    @Scheduled(fixedRate = 30000)
    public void processScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledMessage> dueMessages = scheduledMessageRepository.findByStatusAndScheduledAtBefore(STATUS_PENDING, now);

        if (dueMessages.isEmpty()) {
            return;
        }

        log.info("📤 Processing {} due scheduled messages", dueMessages.size());

        for (ScheduledMessage scheduled : dueMessages) {
            try {
                sendScheduledMessage(scheduled);
            } catch (Exception e) {
                log.error("❌ Failed to send scheduled message {}: {}", scheduled.getId(), e.getMessage());
            }
        }
    }

    private void sendScheduledMessage(ScheduledMessage scheduled) {
        // Build a MessageDTO and delegate to MessageService.createMessage
        MessageDTO messageDTO = new MessageDTO();
        messageDTO.setConversationId(scheduled.getConversationId());
        messageDTO.setSenderId(scheduled.getSenderId());
        messageDTO.setSenderName(scheduled.getSenderName());
        messageDTO.setSenderAvatar(scheduled.getSenderAvatar());
        messageDTO.setContent(scheduled.getContent());

        // MessageDTO uses List<MessageAttachment> directly — assign as-is
        if (scheduled.getAttachments() != null && !scheduled.getAttachments().isEmpty()) {
            messageDTO.setAttachments(scheduled.getAttachments());
        }

        messageService.createMessage(messageDTO);

        // Mark as sent
        scheduled.setStatus(STATUS_SENT);
        scheduled.setUpdatedAt(LocalDateTime.now());
        scheduledMessageRepository.save(scheduled);

        log.info("✅ Scheduled message sent: id={}, conversationId={}", scheduled.getId(), scheduled.getConversationId());
    }

    private ScheduledMessageDTO toDTO(ScheduledMessage entity) {
        ScheduledMessageDTO dto = new ScheduledMessageDTO();
        dto.setId(entity.getId());
        dto.setConversationId(entity.getConversationId());
        dto.setSenderId(entity.getSenderId());
        dto.setSenderName(entity.getSenderName());
        dto.setSenderAvatar(entity.getSenderAvatar());
        dto.setContent(entity.getContent());
        dto.setAttachments(entity.getAttachments());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
