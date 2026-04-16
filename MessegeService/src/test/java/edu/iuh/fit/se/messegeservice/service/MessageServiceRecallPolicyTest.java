package edu.iuh.fit.se.messegeservice.service;

import edu.iuh.fit.se.messegeservice.model.Conversation;
import edu.iuh.fit.se.messegeservice.model.Message;
import edu.iuh.fit.se.messegeservice.repository.ConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.HiddenConversationRepository;
import edu.iuh.fit.se.messegeservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceRecallPolicyTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private HiddenConversationRepository hiddenConversationRepository;

    @Mock
    private SocketEmitterService socketEmitterService;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(messageService, "recallWindowSeconds", 120L);
    }

    @Test
    void deleteMessage_shouldSucceed_whenSenderRecallsWithinWindow() {
        Message message = new Message();
        message.setId("m1");
        message.setConversationId("c1");
        message.setSenderId("u1");
        message.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        message.setDeleted(false);

        Conversation conversation = new Conversation();
        conversation.setId("c1");
        conversation.setParticipantIds(List.of("u1", "u2"));

        when(messageRepository.findById("m1")).thenReturn(Optional.of(message));
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(message));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        messageService.deleteMessage("m1", "u1");

        assertTrue(message.isDeleted());
        verify(messageRepository).save(message);
    }

    @Test
    void deleteMessage_shouldFail_whenRecallWindowExpired() {
        Message message = new Message();
        message.setId("m2");
        message.setConversationId("c2");
        message.setSenderId("u1");
        message.setCreatedAt(LocalDateTime.now().minusSeconds(121));

        when(messageRepository.findById("m2")).thenReturn(Optional.of(message));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> messageService.deleteMessage("m2", "u1")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(messageRepository, never()).save(any(Message.class));
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void deleteMessage_shouldFail_whenRequesterIsNotSender() {
        Message message = new Message();
        message.setId("m3");
        message.setConversationId("c3");
        message.setSenderId("u2");
        message.setCreatedAt(LocalDateTime.now());

        when(messageRepository.findById("m3")).thenReturn(Optional.of(message));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> messageService.deleteMessage("m3", "u1")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(messageRepository, never()).save(any(Message.class));
        verifyNoInteractions(conversationRepository);
    }
}
