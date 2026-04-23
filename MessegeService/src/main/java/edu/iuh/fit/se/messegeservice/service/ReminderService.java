package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.config.socket.SocketEventTypes;
import edu.iuh.fit.se.messegeservice.dto.ReminderDTO;
import edu.iuh.fit.se.messegeservice.dto.SocketEventDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.Reminder;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final SocketEmitterService socketEmitterService;

    public ReminderDTO createReminder(ReminderDTO dto) {
        Reminder reminder = new Reminder();
        reminder.setConversationId(dto.getConversationId());
        reminder.setCreatorId(dto.getCreatorId());
        reminder.setContent(dto.getContent());
        reminder.setRemindAt(dto.getRemindAt());
        reminder.setCreatedAt(LocalDateTime.now());
        reminder.setUpdatedAt(LocalDateTime.now());
        reminder.setTriggered(false);

        Reminder saved = reminderRepository.save(reminder);
        return toDTO(saved);
    }

    public List<ReminderDTO> getReminders(String conversationId) {
        return reminderRepository.findByConversationId(conversationId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void deleteReminder(String id) {
        reminderRepository.deleteById(id);
    }

    /**
     * Runs every 1 minute to check for reminders that are due.
     */
    @Scheduled(fixedRate = 60000)
    public void processReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueReminders = reminderRepository.findByIsTriggeredFalseAndRemindAtBefore(now);
        
        if (dueReminders.isEmpty()) {
            return;
        }
        
        log.info("🔔 Processing {} due reminders", dueReminders.size());
        
        for (Reminder reminder : dueReminders) {
            try {
                triggerReminder(reminder);
            } catch (Exception e) {
                log.error("❌ Failed to trigger reminder {}: {}", reminder.getId(), e.getMessage());
            }
        }
    }

    private void triggerReminder(Reminder reminder) {
        reminder.setTriggered(true);
        reminder.setUpdatedAt(LocalDateTime.now());
        reminderRepository.save(reminder);

        Conversation conversation = conversationRepository.findById(reminder.getConversationId()).orElse(null);
        if (conversation == null) return;

        // 1. Create a system message in the chat
        try {
            messageService.createSystemMessage(
                reminder.getConversationId(), 
                reminder.getCreatorId(), 
                "REMINDER_TRIGGERED", 
                "Nhắc hẹn: " + reminder.getContent()
            );
        } catch (Exception e) {
            log.warn("Failed to create system message for reminder: {}", e.getMessage());
        }

        // 2. Emit Socket Event (Notification type)
        Map<String, Object> data = new HashMap<>();
        data.put("id", reminder.getId());
        data.put("content", reminder.getContent());
        data.put("conversationId", reminder.getConversationId());
        data.put("triggeredAt", LocalDateTime.now().toString());

        SocketEventDTO event = SocketEventDTO.of(SocketEventTypes.REMINDER_TRIGGERED, null, data);
        
        if (conversation.getParticipantIds() != null) {
            for (String userId : conversation.getParticipantIds()) {
                socketEmitterService.emitToUserById(userId, event);
            }
        }
    }

    private ReminderDTO toDTO(Reminder reminder) {
        ReminderDTO dto = new ReminderDTO();
        dto.setId(reminder.getId());
        dto.setConversationId(reminder.getConversationId());
        dto.setCreatorId(reminder.getCreatorId());
        dto.setContent(reminder.getContent());
        dto.setRemindAt(reminder.getRemindAt());
        dto.setTriggered(reminder.isTriggered());
        dto.setCreatedAt(reminder.getCreatedAt());
        dto.setUpdatedAt(reminder.getUpdatedAt());
        return dto;
    }
}
