package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.dto.MessagePageDTO;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.HiddenConversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.model.PollOption;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.HiddenConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_SYSTEM = "SYSTEM";
    private static final String TYPE_POLL = "POLL";
    private static final long DEFAULT_RECALL_WINDOW_SECONDS = 120L;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final HiddenConversationRepository hiddenConversationRepository;
    private final SocketEmitterService socketEmitterService;
    private final CommonServiceClientFacade commonServiceClientFacade;

    @Value("${chat.message.recall-window-seconds:120}")
    private long recallWindowSeconds;

    public List<MessageDTO> getMessagesByConversationId(String conversationId) {
        return getMessagesByConversationId(conversationId, null);
    }

    public List<MessageDTO> getMessagesByConversationId(String conversationId, String userId) {
        LocalDateTime clearCutoff = null;
        if (userId != null && !userId.isBlank()) {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
            ensureParticipant(conversation, userId);
            clearCutoff = resolveClearCutoff(conversationId, userId);
        }
        final LocalDateTime finalClearCutoff = clearCutoff;
        final String finalUserId = userId;

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(msg -> !msg.isDeleted())
                .filter(msg -> finalClearCutoff == null || (msg.getCreatedAt() != null && msg.getCreatedAt().isAfter(finalClearCutoff)))
                .filter(msg -> !isHiddenForUser(msg, finalUserId))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getPinnedMessages(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);
        LocalDateTime clearCutoff = resolveClearCutoff(conversationId, userId);
        final LocalDateTime finalClearCutoff = clearCutoff;
        final String finalUserId = userId;

        return messageRepository
                .findByConversationIdAndIsDeletedFalseAndPinnedTrueOrderByCreatedAtDesc(conversationId)
                .stream()
            .filter(m -> finalClearCutoff == null || (m.getCreatedAt() != null && m.getCreatedAt().isAfter(finalClearCutoff)))
            .filter(m -> !isHiddenForUser(m, finalUserId))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getMediaMessages(String conversationId, String userId, String type) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);
        LocalDateTime clearCutoff = resolveClearCutoff(conversationId, userId);
        final LocalDateTime finalClearCutoff = clearCutoff;
        final String finalUserId = userId;

        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);

        return messageRepository
                .findByConversationIdAndIsDeletedFalseAndAttachmentsIsNotNullOrderByCreatedAtDesc(conversationId)
                .stream()
                .filter(m -> finalClearCutoff == null || (m.getCreatedAt() != null && m.getCreatedAt().isAfter(finalClearCutoff)))
                .filter(m -> !isHiddenForUser(m, finalUserId))
                .filter(m -> m.getAttachments() != null && !m.getAttachments().isEmpty())
                .filter(m -> normalizedType.isBlank() || m.getAttachments().stream().anyMatch(a -> {
                    if (a == null || a.getType() == null) {
                        return false;
                    }
                    return normalizedType.equals(a.getType().trim().toLowerCase(Locale.ROOT));
                }))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public MessagePageDTO getMessagesByConversationCursor(String conversationId, String before, Integer limit) {
        return getMessagesByConversationCursor(conversationId, before, limit, null);
    }

    public MessagePageDTO getMessagesByConversationCursor(String conversationId, String before, Integer limit, String userId) {
        int pageSize = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
        LocalDateTime clearCutoff = null;

        if (userId != null && !userId.isBlank()) {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
            ensureParticipant(conversation, userId);
            clearCutoff = resolveClearCutoff(conversationId, userId);
        }

        List<Message> batch = collectCursorBatch(conversationId, before, clearCutoff, userId, pageSize);
        boolean hasMore = batch.size() > pageSize;
        if (hasMore) {
            batch = new ArrayList<>(batch.subList(0, pageSize));
        }

        List<MessageDTO> payload = batch.stream().map(this::toDTO).collect(Collectors.toList());
        Collections.reverse(payload);

        String nextCursor = null;
        if (!batch.isEmpty()) {
            Message oldest = batch.get(batch.size() - 1);
            if (oldest.getCreatedAt() != null) {
                nextCursor = oldest.getCreatedAt().toString();
            }
        }

        return new MessagePageDTO(payload, nextCursor, hasMore);
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
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
    }

    public List<MessageDTO> searchMessages(String conversationId, String keyword, String userId, String senderId) {
        if (conversationId == null || conversationId.isBlank() || keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        
        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("Requester is not a participant of this conversation");
        }
        
        // Ensure keyword is regex-safe or just use containing
        // We defined findByConversationIdAndContentRegexAndIsDeletedFalseOrderByCreatedAtDesc
        String regex = ".*" + java.util.regex.Pattern.quote(keyword) + ".*";
        
        List<Message> messages = messageRepository.findByConversationIdAndContentRegexAndIsDeletedFalseOrderByCreatedAtDesc(
                conversationId, regex);
                
        return messages.stream()
                .filter(m -> m.getHiddenForUserIds() == null || !m.getHiddenForUserIds().contains(userId))
                .filter(m -> TYPE_TEXT.equals(m.getMessageType())) // Only search in text messages
                .filter(m -> senderId == null || senderId.isBlank() || senderId.equals(m.getSenderId()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStorageStats(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        ensureParticipant(conversation, userId);

        List<Message> messagesWithAttachments = messageRepository
                .findByConversationIdAndIsDeletedFalseAndAttachmentsIsNotNullOrderByCreatedAtDesc(conversationId);

        long totalSize = 0;
        Map<String, Long> sizeByType = new HashMap<>();
        Map<String, Integer> countByType = new HashMap<>();

        for (Message msg : messagesWithAttachments) {
            if (msg.getAttachments() == null) continue;
            for (edu.iuh.fit.se.messegeservice.model.MessageAttachment att : msg.getAttachments()) {
                if (att.getFileSize() != null) {
                    long size = att.getFileSize();
                    totalSize += size;
                    String type = att.getType() != null ? att.getType() : "OTHER";
                    sizeByType.put(type, sizeByType.getOrDefault(type, 0L) + size);
                }
                String type = att.getType() != null ? att.getType() : "OTHER";
                countByType.put(type, countByType.getOrDefault(type, 0) + 1);
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("conversationId", conversationId);
        stats.put("totalSize", totalSize);
        stats.put("sizeByType", sizeByType);
        stats.put("countByType", countByType);
        stats.put("totalCount", countByType.values().stream().mapToInt(Integer::intValue).sum());

        return stats;
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
                    return new ResourceNotFoundException("Conversation not found: " + messageDTO.getConversationId());
                });

        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(messageDTO.getSenderId())) {
            throw new IllegalArgumentException("Sender is not a participant of this conversation");
        }

        ensureCanSend(conversation, messageDTO.getSenderId());

        Message message = toEntity(messageDTO, conversation);
        message.setMessageType(TYPE_TEXT);
        message.setSystemAction(null);
        message.setPollQuestion(null);
        message.setPollOptions(null);
        message.setPollMultipleChoice(false);
        message.setPollClosed(false);
        message.setMentionUserIds(resolveMentionUserIds(messageDTO, conversation));
        message.setSeenByUserIds(new ArrayList<>(List.of(messageDTO.getSenderId())));
        message.setHiddenForUserIds(new ArrayList<>());
        applyReplySnapshotIfPresent(messageDTO, conversation, message);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        message.setDeleted(false);
        message.setEdited(false);
        
        log.info("💾 Before save - conversationId: {}, conversation: {}", 
            message.getConversationId(), 
            message.getConversation() != null ? message.getConversation().getId() : "null");
        
        Message saved = messageRepository.save(message);
        unhideSoftDeletedConversationForParticipants(conversation);
        
        log.info("✅ After save - id: {}, conversationId: {}, conversation: {}", 
            saved.getId(),
            saved.getConversationId(), 
            saved.getConversation() != null ? saved.getConversation().getId() : "null");
        
        log.info("✅ Message saved with id: {}", saved.getId());
        
        // Update conversation last message
        conversation.setLastMessagePreview(buildLastMessagePreview(saved));
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        
        MessageDTO savedDTO = toDTO(saved);
        
        log.info("📤 DTO after conversion - id: {}, conversationId: {}", 
            savedDTO.getId(), savedDTO.getConversationId());
        
        // Emit to other participants; sender already has optimistic local state.
        try {
            emitMessageReceivedToConversation(conversation, savedDTO);
        } catch (Exception e) {
            log.error("❌ Failed to emit socket event for new message: {}", e.getMessage(), e);
            // Don't fail the entire operation if socket emit fails
        }
        
        return savedDTO;
    }

    public MessageDTO updateMessage(String id, MessageDTO messageDTO) {
        if (messageDTO.getSenderId() == null || messageDTO.getSenderId().isBlank()) {
            throw new IllegalArgumentException("senderId is required for editing message");
        }

        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));

        if (message.isDeleted()) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }
        if (!TYPE_TEXT.equalsIgnoreCase(message.getMessageType())) {
            throw new IllegalArgumentException("Only text messages can be edited");
        }
        if (!messageDTO.getSenderId().equals(message.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sender can edit this message");
        }

        Conversation conversation = conversationRepository.findById(message.getConversationId())
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + message.getConversationId()));
        ensureParticipant(conversation, messageDTO.getSenderId());

        String nextContent = messageDTO.getContent() == null ? "" : messageDTO.getContent().trim();
        if (nextContent.isBlank() && (messageDTO.getAttachments() == null || messageDTO.getAttachments().isEmpty())) {
            throw new IllegalArgumentException("Edited message cannot be empty");
        }
        
        message.setContent(nextContent);
        message.setAttachments(messageDTO.getAttachments());
        message.setMentionUserIds(resolveMentionUserIds(messageDTO, conversation));
        message.setEdited(true);
        message.setUpdatedAt(LocalDateTime.now());
        
        Message updated = messageRepository.save(message);
        refreshConversationLastMessage(conversation);
        emitConversationMetaUpdated(conversation);
        emitEventToConversationParticipants(
            conversation,
            SocketEventTypes.MESSAGE_EDITED,
            Map.of("conversationId", conversation.getId(), "message", toDTO(updated)),
            null
        );
        return toDTO(updated);
    }

    public MessageDTO togglePin(String id, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
        Conversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + message.getConversationId()));
        ensureParticipant(conversation, userId);

        message.setPinned(!message.isPinned());
        message.setUpdatedAt(LocalDateTime.now());
        Message updated = messageRepository.save(message);

        emitEventToConversationParticipants(
            conversation,
            SocketEventTypes.MESSAGE_PINNED,
            Map.of(
                "conversationId", conversation.getId(),
                "messageId", updated.getId(),
                "pinned", updated.isPinned()
            ),
            null
        );

        String actorName = resolveParticipantDisplayName(conversation, userId);
        String systemAction = updated.isPinned() ? "PINNED" : "UNPINNED";
        String content = updated.isPinned()
                ? actorName + " da ghim mot tin nhan"
                : actorName + " da bo ghim mot tin nhan";
        createAndEmitSystemMessage(conversation, userId, systemAction, content);

        return toDTO(updated);
    }

    public MessageDTO toggleStar(String id, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required to star a message");
        }
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));

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
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));

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
        Message updated = messageRepository.save(message);

        Conversation conversation = conversationRepository.findById(updated.getConversationId()).orElse(null);
        if (conversation != null) {
            emitEventToConversationParticipants(
                    conversation,
                    SocketEventTypes.MESSAGE_REACTED,
                    Map.of(
                            "conversationId", conversation.getId(),
                            "messageId", updated.getId(),
                            "emojis", updated.getEmojis()
                    ),
                    null
            );
        }

        return toDTO(updated);
    }

    public MessageDTO createPoll(
            String conversationId,
            String userId,
            String question,
            List<String> options,
            boolean multipleChoice
    ) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);
        ensureCanSend(conversation, userId);

        if (question == null || question.trim().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }

        List<String> normalizedOptions = (options == null ? new ArrayList<String>() : options)
                .stream()
                .filter(o -> o != null && !o.trim().isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (normalizedOptions.size() < 2) {
            throw new IllegalArgumentException("Poll requires at least 2 options");
        }

        Message poll = new Message();
        poll.setConversation(conversation);
        poll.setConversationId(conversation.getId());
        poll.setSenderId(userId);
        poll.setSenderName(resolveParticipantDisplayName(conversation, userId));
        poll.setMessageType(TYPE_POLL);
        poll.setContent(question.trim());
        poll.setPollQuestion(question.trim());
        poll.setPollMultipleChoice(multipleChoice);
        poll.setPollClosed(false);
        poll.setPollOptions(
                java.util.stream.IntStream.range(0, normalizedOptions.size())
                        .mapToObj(i -> new PollOption("opt-" + (i + 1), normalizedOptions.get(i), new ArrayList<>()))
                        .collect(Collectors.toList())
        );
        poll.setSeenByUserIds(new ArrayList<>(List.of(userId)));
        poll.setHiddenForUserIds(new ArrayList<>());
        poll.setDeleted(false);
        poll.setEdited(false);
        poll.setCreatedAt(LocalDateTime.now());
        poll.setUpdatedAt(LocalDateTime.now());

        Message saved = messageRepository.save(poll);
        unhideSoftDeletedConversationForParticipants(conversation);

        conversation.setLastMessagePreview(buildLastMessagePreview(saved));
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageDTO savedDTO = toDTO(saved);
        emitMessageReceivedToConversation(conversation, savedDTO);

        // ── Phase C: AI Assistant Stub ──────────────────────────────────────────
        if (conversation.isAiAssistantEnabled() && saved.getContent() != null && saved.getContent().contains("@ZalaBot")) {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Simulate processing
                    createAndEmitSystemMessage(conversation, "ZalaBot", "AI_REPLY", 
                        "Chao ban! Toi la ZalaBot. Hien tai toi dang trong qua trinh nang cap, hay thu lai sau nhe!");
                } catch (InterruptedException ignored) {}
            }).start();
        }

        String actorName = resolveParticipantDisplayName(conversation, userId);
        createAndEmitSystemMessage(conversation, userId, SocketEventTypes.POLL_CREATED, actorName + " da tao binh chon");

        return savedDTO;
    }

    public MessageDTO votePoll(String messageId, String userId, List<String> optionIds) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        Message poll = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + messageId));
        if (!TYPE_POLL.equalsIgnoreCase(poll.getMessageType())) {
            throw new IllegalArgumentException("Message is not a poll");
        }
        if (poll.isPollClosed()) {
            throw new IllegalStateException("Poll has been closed");
        }

        Conversation conversation = conversationRepository.findById(poll.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + poll.getConversationId()));
        ensureParticipant(conversation, userId);

        List<String> selectedOptionIds = (optionIds == null ? new ArrayList<String>() : optionIds)
                .stream()
                .filter(o -> o != null && !o.trim().isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (selectedOptionIds.isEmpty()) {
            throw new IllegalArgumentException("optionIds cannot be empty");
        }
        if (!poll.isPollMultipleChoice() && selectedOptionIds.size() > 1) {
            throw new IllegalArgumentException("This poll only allows one choice");
        }

        List<PollOption> options = poll.getPollOptions();
        if (options == null || options.isEmpty()) {
            throw new IllegalStateException("Poll has no options");
        }

        Set<String> validOptionIds = options.stream().map(PollOption::getOptionId).collect(Collectors.toSet());
        for (String selectedOptionId : selectedOptionIds) {
            if (!validOptionIds.contains(selectedOptionId)) {
                throw new IllegalArgumentException("Invalid poll option: " + selectedOptionId);
            }
        }

        for (PollOption option : options) {
            List<String> voters = option.getVoterUserIds();
            if (voters == null) {
                voters = new ArrayList<>();
            }
            voters.remove(userId);
            if (selectedOptionIds.contains(option.getOptionId())) {
                voters.add(userId);
            }
            option.setVoterUserIds(voters.stream().distinct().collect(Collectors.toList()));
        }

        poll.setPollOptions(options);
        poll.setUpdatedAt(LocalDateTime.now());

        Message updated = messageRepository.save(poll);
        MessageDTO dto = toDTO(updated);

        emitEventToConversationParticipants(
                conversation,
                SocketEventTypes.POLL_UPDATED,
                Map.of(
                        "conversationId", conversation.getId(),
                        "message", dto
                ),
                null
        );

        return dto;
    }

    public void emitTypingEvent(String conversationId, String userId, boolean typing) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("typing", typing);

        emitEventToConversationParticipants(conversation, SocketEventTypes.TYPING, payload, userId);
    }

    public void markSeen(String conversationId, String userId, String lastSeenMessageId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);

        List<Message> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (all.isEmpty()) {
            return;
        }

        Message target = null;
        if (lastSeenMessageId != null && !lastSeenMessageId.isBlank()) {
            target = all.stream()
                    .filter(m -> lastSeenMessageId.equals(m.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (target == null) {
            for (int i = all.size() - 1; i >= 0; i--) {
                Message m = all.get(i);
                if (!m.isDeleted() && !userId.equals(m.getSenderId()) && !isHiddenForUser(m, userId)) {
                    target = m;
                    break;
                }
            }
        }
        if (target == null || target.getCreatedAt() == null) {
            return;
        }

        LocalDateTime cutoff = target.getCreatedAt();
        List<Message> changed = new ArrayList<>();
        for (Message m : all) {
            if (m.isDeleted()) {
                continue;
            }
            if (isHiddenForUser(m, userId)) {
                continue;
            }
            if (userId.equals(m.getSenderId())) {
                continue;
            }
            if (m.getCreatedAt() == null || m.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            List<String> seenBy = m.getSeenByUserIds();
            if (seenBy == null) {
                seenBy = new ArrayList<>();
            }
            if (!seenBy.contains(userId)) {
                seenBy.add(userId);
                m.setSeenByUserIds(seenBy);
                m.setUpdatedAt(LocalDateTime.now());
                changed.add(m);
            }
        }

        if (!changed.isEmpty()) {
            messageRepository.saveAll(changed);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("lastSeenMessageId", target.getId());
        payload.put("seenAt", LocalDateTime.now().toString());

        emitEventToConversationParticipants(conversation, SocketEventTypes.MESSAGE_SEEN, payload, userId);
    }

    public void markDelivered(String conversationId, String userId, String lastDeliveredMessageId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        ensureParticipant(conversation, userId);

        List<Message> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (all.isEmpty()) {
            return;
        }

        Message target = null;
        if (lastDeliveredMessageId != null && !lastDeliveredMessageId.isBlank()) {
            target = all.stream()
                    .filter(m -> lastDeliveredMessageId.equals(m.getId()))
                    .findFirst()
                    .orElse(null);
        }
        if (target == null) {
            for (int i = all.size() - 1; i >= 0; i--) {
                Message m = all.get(i);
                if (!m.isDeleted() && !userId.equals(m.getSenderId()) && !isHiddenForUser(m, userId)) {
                    target = m;
                    break;
                }
            }
        }
        if (target == null || target.getCreatedAt() == null) {
            return;
        }

        LocalDateTime cutoff = target.getCreatedAt();
        List<Message> changed = new ArrayList<>();
        for (Message m : all) {
            if (m.isDeleted()) {
                continue;
            }
            if (isHiddenForUser(m, userId)) {
                continue;
            }
            if (userId.equals(m.getSenderId())) {
                continue;
            }
            if (m.getCreatedAt() == null || m.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            List<String> deliveredTo = m.getDeliveredToUserIds();
            if (deliveredTo == null) {
                deliveredTo = new ArrayList<>();
            }
            // Cannot deliver if already seen by this user? Doesn't matter, just add to deliveredTo
            if (!deliveredTo.contains(userId)) {
                deliveredTo.add(userId);
                m.setDeliveredToUserIds(deliveredTo);
                m.setUpdatedAt(LocalDateTime.now());
                changed.add(m);
            }
        }

        if (!changed.isEmpty()) {
            messageRepository.saveAll(changed);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("lastDeliveredMessageId", target.getId());
        payload.put("deliveredAt", LocalDateTime.now().toString());

        emitEventToConversationParticipants(conversation, SocketEventTypes.MESSAGE_DELIVERED, payload, userId);
    }

    public void deleteMessage(String id, String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
        if (!message.getSenderId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can delete this message");
        }
        ensureWithinRecallWindow(message);
        message.setDeleted(true);
        message.setUpdatedAt(LocalDateTime.now());
        messageRepository.save(message);

        Conversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        if (conversation != null && conversation.getParticipantIds() != null) {
            refreshConversationLastMessage(conversation);
            emitConversationMetaUpdated(conversation);
            Map<String, String> payload = new HashMap<>();
            payload.put("conversationId", message.getConversationId());
            payload.put("messageId", id);
            for (String participantId : conversation.getParticipantIds()) {
                try {
                    socketEmitterService.emitToUserById(
                            participantId,
                            SocketEventDTO.messageDeleted(participantId, payload));
                } catch (Exception e) {
                    log.warn("Failed to emit MESSAGE_DELETED to {}: {}", participantId, e.getMessage());
                }
            }
        }
    }

    public void deleteMessageForMe(String id, String requesterId) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
        Conversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + message.getConversationId()));
        ensureParticipant(conversation, requesterId);

        List<String> hiddenForUserIds = message.getHiddenForUserIds();
        if (hiddenForUserIds == null) {
            hiddenForUserIds = new ArrayList<>();
        }
        if (!hiddenForUserIds.contains(requesterId)) {
            hiddenForUserIds.add(requesterId);
            message.setHiddenForUserIds(hiddenForUserIds);
            message.setUpdatedAt(LocalDateTime.now());
            messageRepository.save(message);
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("conversationId", message.getConversationId());
        payload.put("messageId", id);
        socketEmitterService.emitToUserById(
                requesterId,
                SocketEventDTO.of(SocketEventTypes.MESSAGE_DELETED_FOR_ME, requesterId, payload)
        );
    }

    public MessageDTO forwardMessage(String sourceMessageId, String requesterId, String targetConversationId, String note) {
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("requesterId is required");
        }
        if (targetConversationId == null || targetConversationId.isBlank()) {
            throw new IllegalArgumentException("targetConversationId is required");
        }

        Message source = messageRepository.findById(sourceMessageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + sourceMessageId));
        if (source.isDeleted()) {
            throw new IllegalArgumentException("Cannot forward a deleted message");
        }
        if (isHiddenForUser(source, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot forward this message");
        }

        Conversation sourceConversation = conversationRepository.findById(source.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + source.getConversationId()));
        ensureParticipant(sourceConversation, requesterId);

        Conversation targetConversation = conversationRepository.findById(targetConversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + targetConversationId));
        ensureParticipant(targetConversation, requesterId);
        ensureCanSend(targetConversation, requesterId);

        Message forwarded = new Message();
        forwarded.setConversation(targetConversation);
        forwarded.setConversationId(targetConversation.getId());
        forwarded.setSenderId(requesterId);
        forwarded.setSenderName(resolveParticipantDisplayName(targetConversation, requesterId));
        forwarded.setSenderAvatar(null);
        forwarded.setMessageType(TYPE_TEXT);
        forwarded.setSystemAction(null);
        forwarded.setContent(buildForwardedContent(source, note));
        forwarded.setAttachments(source.getAttachments() == null ? null : new ArrayList<>(source.getAttachments()));
        forwarded.setMentionUserIds(new ArrayList<>());
        forwarded.setSeenByUserIds(new ArrayList<>(List.of(requesterId)));
        forwarded.setHiddenForUserIds(new ArrayList<>());
        forwarded.setDeleted(false);
        forwarded.setEdited(false);
        forwarded.setCreatedAt(LocalDateTime.now());
        forwarded.setUpdatedAt(LocalDateTime.now());

        Message saved = messageRepository.save(forwarded);
        unhideSoftDeletedConversationForParticipants(targetConversation);

        targetConversation.setLastMessagePreview(buildLastMessagePreview(saved));
        targetConversation.setLastMessageAt(saved.getCreatedAt());
        targetConversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(targetConversation);
        emitConversationMetaUpdated(targetConversation);

        MessageDTO dto = toDTO(saved);
        emitMessageReceivedToConversation(targetConversation, dto);
        return dto;
    }

    public MessageDTO createSystemMessage(String conversationId, String actorUserId, String action, String content) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        Message saved = createAndEmitSystemMessage(conversation, actorUserId, action, content);
        return toDTO(saved);
    }

    private void applyReplySnapshotIfPresent(MessageDTO messageDTO, Conversation conversation, Message message) {
        String replyId = messageDTO.getReplyToMessageId();
        if (replyId == null || replyId.isBlank()) {
            return;
        }
        Message replied = messageRepository.findById(replyId)
                .orElseThrow(() -> new IllegalArgumentException("Reply target not found"));
        if (!conversation.getId().equals(replied.getConversationId())) {
            throw new IllegalArgumentException("Reply target must be in the same conversation");
        }
        if (replied.isDeleted()) {
            throw new IllegalArgumentException("Cannot reply to a deleted message");
        }
        message.setReplyToMessageId(replied.getId());
        message.setReplyToSenderName(replied.getSenderName());
        message.setReplyToContentPreview(buildReplyContentPreview(replied));
    }

    private static String buildReplyContentPreview(Message replied) {
        String c = replied.getContent();
        if (c != null && !c.trim().isEmpty()) {
            return c.length() > 200 ? c.substring(0, 200) : c;
        }
        if (replied.getAttachments() != null && !replied.getAttachments().isEmpty()) {
            return "[📎]";
        }
        return "";
    }

    private static String buildLastMessagePreview(Message saved) {
        if (TYPE_SYSTEM.equalsIgnoreCase(saved.getMessageType())) {
            String content = saved.getContent();
            return content == null ? "" : (content.length() > 80 ? content.substring(0, 80) + "…" : content);
        }
        if (TYPE_POLL.equalsIgnoreCase(saved.getMessageType())) {
            String question = saved.getPollQuestion() != null ? saved.getPollQuestion() : saved.getContent();
            if (question == null) {
                return "📊 Binh chon";
            }
            return ("📊 " + question).length() > 80 ? ("📊 " + question).substring(0, 80) + "…" : "📊 " + question;
        }

        String content = saved.getContent();
        if (content != null && !content.trim().isEmpty()) {
            return content.length() > 80 ? content.substring(0, 80) + "…" : content;
        }
        if (saved.getAttachments() != null && !saved.getAttachments().isEmpty()) {
            return "📎";
        }
        if (saved.getReplyToMessageId() != null) {
            String p = saved.getReplyToContentPreview();
            return "↩ " + (p != null ? p : "");
        }
        return "";
    }

    private String buildForwardedContent(Message source, String note) {
        String sourceContent = source.getContent();
        if (sourceContent == null || sourceContent.trim().isEmpty()) {
            if (source.getAttachments() != null && !source.getAttachments().isEmpty()) {
                sourceContent = "[Forwarded attachment]";
            } else if (TYPE_POLL.equalsIgnoreCase(source.getMessageType())) {
                sourceContent = "[Forwarded poll]";
            } else if (TYPE_SYSTEM.equalsIgnoreCase(source.getMessageType())) {
                sourceContent = "[Forwarded system message]";
            } else {
                sourceContent = "[Forwarded message]";
            }
        }

        if (note == null || note.trim().isEmpty()) {
            return sourceContent;
        }

        return note.trim() + "\n" + sourceContent;
    }

    private List<Message> collectCursorBatch(
            String conversationId,
            String before,
            LocalDateTime clearCutoff,
            String userId,
            int pageSize
    ) {
        final int fetchSize = Math.min(Math.max(pageSize * 3, pageSize + 1), 100);
        LocalDateTime cursor = (before == null || before.isBlank()) ? null : parseCursor(before);
        List<Message> collected = new ArrayList<>();

        for (int i = 0; i < 10 && collected.size() <= pageSize; i++) {
            List<Message> rawBatch = fetchRawCursorBatch(conversationId, cursor, clearCutoff, fetchSize);
            if (rawBatch.isEmpty()) {
                break;
            }

            for (Message m : rawBatch) {
                if (userId != null && !userId.isBlank() && isHiddenForUser(m, userId)) {
                    continue;
                }
                collected.add(m);
                if (collected.size() > pageSize) {
                    break;
                }
            }

            if (rawBatch.size() < fetchSize) {
                break;
            }

            Message oldest = rawBatch.get(rawBatch.size() - 1);
            if (oldest.getCreatedAt() == null) {
                break;
            }
            cursor = oldest.getCreatedAt();
        }

        return collected;
    }

    private List<Message> fetchRawCursorBatch(
            String conversationId,
            LocalDateTime before,
            LocalDateTime clearCutoff,
            int fetchSize
    ) {
        Pageable pageable = PageRequest.of(0, fetchSize);

        if (before == null) {
            if (clearCutoff == null) {
                return messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageable);
            }
            return messageRepository.findByConversationIdAndIsDeletedFalseAndCreatedAtAfterOrderByCreatedAtDesc(
                    conversationId,
                    clearCutoff,
                    pageable
            );
        }

        if (clearCutoff == null) {
            return messageRepository.findByConversationIdAndIsDeletedFalseAndCreatedAtBeforeOrderByCreatedAtDesc(
                    conversationId,
                    before,
                    pageable
            );
        }

        return messageRepository.findByConversationIdAndIsDeletedFalseAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtDesc(
                conversationId,
                clearCutoff,
                before,
                pageable
        );
    }

    private boolean isHiddenForUser(Message message, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<String> hiddenForUserIds = message.getHiddenForUserIds();
        return hiddenForUserIds != null && hiddenForUserIds.contains(userId);
    }

    private void refreshConversationLastMessage(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }

        // Fetch only the latest non-deleted message (limit 1) instead of loading ALL messages
        List<Message> latest = messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(
                conversation.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1)
        );

        if (!latest.isEmpty()) {
            conversation.setLastMessagePreview(buildLastMessagePreview(latest.get(0)));
            conversation.setLastMessageAt(latest.get(0).getCreatedAt());
        } else {
            conversation.setLastMessagePreview("");
            conversation.setLastMessageAt(null);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private void ensureWithinRecallWindow(Message message) {
        if (message == null || message.getCreatedAt() == null) {
            return;
        }

        long elapsedSeconds = Duration.between(message.getCreatedAt(), LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > getEffectiveRecallWindowSeconds()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recall window expired. You can recall a message within " + getEffectiveRecallWindowSeconds() + " seconds."
            );
        }
    }

    private long getEffectiveRecallWindowSeconds() {
        return recallWindowSeconds > 0 ? recallWindowSeconds : DEFAULT_RECALL_WINDOW_SECONDS;
    }

    private void emitConversationMetaUpdated(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversation.getId());
        payload.put("lastMessagePreview", conversation.getLastMessagePreview());
        payload.put(
                "lastMessageAt",
                conversation.getLastMessageAt() != null ? conversation.getLastMessageAt().toString() : null
        );
        emitEventToConversationParticipants(conversation, SocketEventTypes.CONVERSATION_META_UPDATED, payload, null);
    }

    private LocalDateTime parseCursor(String cursor) {
        try {
            return LocalDateTime.parse(cursor);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(cursor).toLocalDateTime();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
        }
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
        dto.setMessageType(message.getMessageType());
        dto.setSystemAction(message.getSystemAction());
        dto.setContent(message.getContent());
        dto.setEmojis(message.getEmojis());
        dto.setAttachments(message.getAttachments());
        dto.setPollQuestion(message.getPollQuestion());
        dto.setPollMultipleChoice(message.isPollMultipleChoice());
        dto.setPollClosed(message.isPollClosed());
        dto.setPollOptions(message.getPollOptions());
        dto.setMentionUserIds(message.getMentionUserIds());
        dto.setSeenByUserIds(message.getSeenByUserIds());
        dto.setDeliveredToUserIds(message.getDeliveredToUserIds());
        dto.setPinned(message.isPinned());
        dto.setStarredByUserIds(message.getStarredByUserIds());
        dto.setDeleted(message.isDeleted());
        dto.setEdited(message.isEdited());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setUpdatedAt(message.getUpdatedAt());
        if (message.getReplyToMessageId() != null) {
            dto.setReplyTo(new MessageDTO.ReplyToPreview(
                    message.getReplyToMessageId(),
                    message.getReplyToSenderName(),
                    message.getReplyToContentPreview()));
        }
        return dto;
    }

    private Message toEntity(MessageDTO dto, Conversation conversation) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setConversationId(conversation.getId()); // 🔥 Set conversationId explicitly
        message.setSenderId(dto.getSenderId());
        message.setSenderName(dto.getSenderName());
        message.setSenderAvatar(dto.getSenderAvatar());
        message.setMessageType(dto.getMessageType() == null ? TYPE_TEXT : dto.getMessageType());
        message.setSystemAction(dto.getSystemAction());
        message.setContent(dto.getContent());
        message.setEmojis(dto.getEmojis());
        message.setAttachments(dto.getAttachments());
        message.setPollQuestion(dto.getPollQuestion());
        message.setPollMultipleChoice(Boolean.TRUE.equals(dto.getPollMultipleChoice()));
        message.setPollClosed(Boolean.TRUE.equals(dto.getPollClosed()));
        message.setPollOptions(dto.getPollOptions());
        message.setMentionUserIds(dto.getMentionUserIds());
        message.setSeenByUserIds(dto.getSeenByUserIds());
        message.setDeliveredToUserIds(dto.getDeliveredToUserIds());
        message.setPinned(dto.getPinned() != null && dto.getPinned());
        message.setStarredByUserIds(dto.getStarredByUserIds());
        return message;
    }

    private void ensureCanSend(Conversation conversation, String userId) {
        // Check if user is banned from this group
        if (conversation.getBannedUserIds() != null && conversation.getBannedUserIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are banned from this conversation");
        }

        // Check if user is blocked in 1-on-1 conversation
        if (!conversation.isGroup() && conversation.getBlockedByUserIds() != null && !conversation.getBlockedByUserIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot send messages in a blocked conversation");
        }

        // ── Privacy check: stranger blocking for DM conversations ──
        if (!conversation.isGroup() && conversation.getParticipantIds() != null && conversation.getParticipantIds().size() == 2) {
            String receiverId = conversation.getParticipantIds().stream()
                    .filter(pid -> !pid.equals(userId))
                    .findFirst().orElse(null);
            if (receiverId != null) {
                boolean allowed = commonServiceClientFacade.canMessage(userId, receiverId);
                if (!allowed) {
                    log.warn("🚫 [Privacy] Message blocked: {} → {} (receiver has FRIENDS_ONLY)", userId, receiverId);
                    // Emit MESSAGE_BLOCKED event to sender in realtime
                    try {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("conversationId", conversation.getId());
                        payload.put("receiverId", receiverId);
                        payload.put("reason", "PRIVACY_FRIENDS_ONLY");
                        SocketEventDTO blockedEvent = SocketEventDTO.of(
                                SocketEventTypes.MESSAGE_BLOCKED,
                                userId,
                                payload
                        );
                        socketEmitterService.emitToUserById(userId, blockedEvent);
                    } catch (Exception e) {
                        log.error("Failed to emit MESSAGE_BLOCKED event: {}", e.getMessage());
                    }
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Cannot send message: recipient only accepts messages from friends");
                }
            }
        }

        // Check onlyAdminsCanSend permission for groups
        if (conversation.isGroup() && conversation.isOnlyAdminsCanSend()) {
            boolean isOwner = conversation.getOwnerId() != null && conversation.getOwnerId().equals(userId);
            boolean isAdmin = conversation.getAdminIds() != null && conversation.getAdminIds().contains(userId);
            if (!isOwner && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owner/admin can send messages in this group");
            }
        }
    }

    private String resolveParticipantDisplayName(Conversation conversation, String userId) {
        if (conversation.getParticipantIds() != null && conversation.getParticipantNames() != null) {
            int idx = conversation.getParticipantIds().indexOf(userId);
            if (idx >= 0 && idx < conversation.getParticipantNames().size()) {
                String name = conversation.getParticipantNames().get(idx);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }
        return userId;
    }

    private void emitMessageReceivedToConversation(Conversation conversation, MessageDTO messageDTO) {
        if (conversation.getParticipantIds() == null || conversation.getParticipantIds().isEmpty()) {
            return;
        }

        // Pre-cache usernames from conversation data to avoid Feign calls
        preCacheUsernamesFromConversation(conversation);

        SocketEventDTO roomEvent = SocketEventDTO.of(SocketEventTypes.MESSAGE_RECEIVED, null, messageDTO);
        socketEmitterService.emitToRoom(conversation.getId(), roomEvent);

        // Emit to individual users asynchronously to avoid blocking the API response
        Thread.startVirtualThread(() -> {
            for (String participantId : conversation.getParticipantIds()) {
                try {
                    socketEmitterService.emitToUserById(
                            participantId,
                            cloneForRecipient(roomEvent, participantId)
                    );
                } catch (Exception e) {
                    log.warn("Failed to emit MESSAGE_RECEIVED to {}: {}", participantId, e.getMessage());
                }
            }
        });
    }

    private Message createAndEmitSystemMessage(Conversation conversation, String actorUserId, String action, String content) {
        Message systemMessage = new Message();
        systemMessage.setConversation(conversation);
        systemMessage.setConversationId(conversation.getId());
        systemMessage.setSenderId(actorUserId);
        systemMessage.setSenderName(resolveParticipantDisplayName(conversation, actorUserId));
        systemMessage.setMessageType(TYPE_SYSTEM);
        systemMessage.setSystemAction(action);
        systemMessage.setContent(content);
        systemMessage.setSeenByUserIds(actorUserId == null ? new ArrayList<>() : new ArrayList<>(List.of(actorUserId)));
        systemMessage.setHiddenForUserIds(new ArrayList<>());
        systemMessage.setDeleted(false);
        systemMessage.setEdited(false);
        systemMessage.setCreatedAt(LocalDateTime.now());
        systemMessage.setUpdatedAt(LocalDateTime.now());

        Message savedSystemMessage = messageRepository.save(systemMessage);
        unhideSoftDeletedConversationForParticipants(conversation);

        conversation.setLastMessagePreview(buildLastMessagePreview(savedSystemMessage));
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        emitMessageReceivedToConversation(conversation, toDTO(savedSystemMessage));

        return savedSystemMessage;
    }

    private LocalDateTime resolveClearCutoff(String conversationId, String userId) {
        return hiddenConversationRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .map(HiddenConversation::getClearBeforeAt)
                .orElse(null);
    }

    private void unhideSoftDeletedConversationForParticipants(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }
        List<HiddenConversation> rows = hiddenConversationRepository
                .findByConversationIdAndHiddenTrueAndRequirePinUnlockFalse(conversation.getId());
        if (rows.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (HiddenConversation row : rows) {
            row.setHidden(false);
            row.setLastAccessAt(now);
            row.setUpdatedAt(now);
        }
        hiddenConversationRepository.saveAll(rows);
    }

    private List<String> resolveMentionUserIds(MessageDTO dto, Conversation conversation) {
        if (dto.getMentionUserIds() != null && !dto.getMentionUserIds().isEmpty()) {
            Set<String> explicit = new HashSet<>(dto.getMentionUserIds());
            explicit.retainAll(new HashSet<>(conversation.getParticipantIds()));
            return new ArrayList<>(explicit);
        }

        String content = dto.getContent();
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        List<String> participantNames = conversation.getParticipantNames();
        List<String> participantIds = conversation.getParticipantIds();
        if (participantNames == null || participantIds == null) {
            return new ArrayList<>();
        }

        Set<String> mentionIds = new HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@\\[([^\\]]+)]").matcher(content);
        while (matcher.find()) {
            String mentionName = matcher.group(1);
            for (int i = 0; i < participantNames.size() && i < participantIds.size(); i++) {
                String n = participantNames.get(i);
                if (n != null && n.equalsIgnoreCase(mentionName)) {
                    mentionIds.add(participantIds.get(i));
                    break;
                }
            }
        }
        return new ArrayList<>(mentionIds);
    }

    private void ensureParticipant(Conversation conversation, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a participant of this conversation");
        }
    }

    private void emitEventToConversationParticipants(
            Conversation conversation,
            String type,
            Object payload,
            String excludeUserId
    ) {
        if (conversation.getParticipantIds() == null || conversation.getParticipantIds().isEmpty()) {
            return;
        }

        // Pre-cache usernames from conversation data to avoid Feign calls
        preCacheUsernamesFromConversation(conversation);

        SocketEventDTO roomEvent = SocketEventDTO.of(type, null, payload);
        socketEmitterService.emitToRoom(conversation.getId(), roomEvent);

        // Emit to individual users asynchronously
        Thread.startVirtualThread(() -> {
            for (String participantId : conversation.getParticipantIds()) {
                if (excludeUserId != null && excludeUserId.equals(participantId)) {
                    continue;
                }
                try {
                    socketEmitterService.emitToUserById(participantId, cloneForRecipient(roomEvent, participantId));
                } catch (Exception e) {
                    log.warn("Failed to emit {} to {}: {}", type, participantId, e.getMessage());
                }
            }
        });
    }

    /**
     * Pre-cache userId → username mappings from the conversation's participant data
     * so that emitToUserById() won't need to make blocking Feign calls.
     */
    private void preCacheUsernamesFromConversation(Conversation conversation) {
        // Conversation stores participantIds and participantNames in parallel lists.
        // We can use this to warm the SocketEmitterService's username cache.
        if (conversation.getParticipantIds() == null || conversation.getParticipantNames() == null) {
            return;
        }
        List<String> ids = conversation.getParticipantIds();
        List<String> names = conversation.getParticipantNames();
        // The names are display names (e.g. "Trần Văn Minh"), not usernames.
        // We can't use them as STOMP usernames. The SocketEmitterService needs
        // the actual username (login name) which must come from AuthService.
        // However, we can batch-prefetch the usernames here to warm the cache.
        socketEmitterService.preCacheUsernames(ids);
    }

    private SocketEventDTO cloneForRecipient(SocketEventDTO source, String recipientUserId) {
        SocketEventDTO cloned = new SocketEventDTO();
        cloned.setEventId(source.getEventId());
        cloned.setType(source.getType());
        cloned.setUserId(recipientUserId);
        cloned.setData(source.getData());
        cloned.setTimestamp(source.getTimestamp());
        return cloned;
    }
}

