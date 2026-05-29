package edu.iuh.fit.se.messegeservice.saga;

import edu.iuh.fit.se.messegeservice.dto.MessageDTO;
import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import edu.iuh.fit.se.messegeservice.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventConsumer {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;

    @KafkaListener(topics = "ttvv.message.created", groupId = "messegeservice-message")
    public void handleMessageCreatedEvent(MessageDTO messageDTO) {
        log.info("📥 [Kafka] Nhận sự kiện tạo tin nhắn mới từ Kafka, ID: {}", messageDTO.getId());
        try {
            if (messageDTO.getId() == null) {
                log.warn("⚠️ Bỏ qua vì tin nhắn không có ID");
                return;
            }

            // Đảm bảo Idempotent: Nếu đã có trong DB thì không lưu lại
            if (messageRepository.existsById(messageDTO.getId())) {
                log.info("✅ Tin nhắn {} đã tồn tại, bỏ qua việc lưu", messageDTO.getId());
                return;
            }

            Optional<Conversation> conversationOpt = conversationRepository.findById(messageDTO.getConversationId());
            if (conversationOpt.isEmpty()) {
                log.warn("⚠️ Không tìm thấy Conversation {}, bỏ qua...", messageDTO.getConversationId());
                return;
            }

            Conversation conversation = conversationOpt.get();
            Message message = messageService.toEntity(messageDTO, conversation);
            message.setId(messageDTO.getId());
            
            // Thực hiện tác vụ I/O nặng nề dưới background
            messageRepository.save(message);
            
            messageService.updateConversationLastMessage(conversation, message);
            conversationRepository.save(conversation);
            
            log.info("✅ [Kafka] Đã lưu thành công tin nhắn {} vào MongoDB ngầm!", message.getId());

        } catch (Exception e) {
            log.error("❌ Lỗi xử lý sự kiện lưu tin nhắn Kafka: {}", e.getMessage(), e);
        }
    }
}
