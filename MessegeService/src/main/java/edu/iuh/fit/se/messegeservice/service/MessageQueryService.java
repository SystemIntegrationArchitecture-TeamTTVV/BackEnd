package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.exception.ResourceNotFoundException;
import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.dto.MessagePageDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.HiddenConversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.HiddenConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueryService {

    private static final String TYPE_TEXT = "TEXT";
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final HiddenConversationRepository hiddenConversationRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

        // Đọc từ Redis Cache (Recent Chats) nếu không truyền cursor phân trang
        if ((before == null || before.isBlank()) && clearCutoff == null) {
            String redisKey = "chat:recent:" + conversationId;
            try {
                List<String> cachedMessages = stringRedisTemplate.opsForList().range(redisKey, 0, pageSize - 1);
                if (cachedMessages != null && !cachedMessages.isEmpty()) {
                    List<MessageDTO> payload = new ArrayList<>();
                    for (String json : cachedMessages) {
                        MessageService.MessageCacheItem item = objectMapper.readValue(json, MessageService.MessageCacheItem.class);
                        boolean isHidden = item.hiddenForUserIds != null && item.hiddenForUserIds.contains(userId);
                        if (!isHidden) {
                            payload.add(item.dto);
                        }
                    }
                    Collections.reverse(payload);
                    String nextCursor = null;
                    if (!payload.isEmpty()) {
                        nextCursor = payload.get(0).getCreatedAt().toString();
                    }
                    boolean hasMore = cachedMessages.size() >= pageSize;
                    return new MessagePageDTO(payload, nextCursor, hasMore);
                }
            } catch (Exception e) {
                log.warn("Failed to read messages from Redis", e);
            }
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

        String regex = ".*" + java.util.regex.Pattern.quote(keyword) + ".*";

        List<Message> messages = messageRepository.findByConversationIdAndContentRegexAndIsDeletedFalseOrderByCreatedAtDesc(
                conversationId, regex);

        return messages.stream()
                .filter(m -> m.getHiddenForUserIds() == null || !m.getHiddenForUserIds().contains(userId))
                .filter(m -> TYPE_TEXT.equals(m.getMessageType()))
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

    private MessageDTO toDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());

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
        dto.setPollCanAddOptions(message.isPollCanAddOptions());
        dto.setPollHideResultsBeforeVote(message.isPollHideResultsBeforeVote());
        dto.setPollHideVoters(message.isPollHideVoters());
        dto.setPollOptions(message.getPollOptions());
        dto.setPollDeadline(message.getPollDeadline());
        dto.setAppointmentTitle(message.getAppointmentTitle());
        dto.setAppointmentTime(message.getAppointmentTime());
        dto.setAppointmentLocation(message.getAppointmentLocation());
        dto.setAppointmentParticipants(message.getAppointmentParticipants());
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

    private LocalDateTime resolveClearCutoff(String conversationId, String userId) {
        return hiddenConversationRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .map(HiddenConversation::getClearBeforeAt)
                .orElse(null);
    }

    private boolean isHiddenForUser(Message message, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<String> hiddenForUserIds = message.getHiddenForUserIds();
        return hiddenForUserIds != null && hiddenForUserIds.contains(userId);
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

    private void ensureParticipant(Conversation conversation, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (conversation.getParticipantIds() == null || !conversation.getParticipantIds().contains(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a participant of this conversation");
        }
    }
}
