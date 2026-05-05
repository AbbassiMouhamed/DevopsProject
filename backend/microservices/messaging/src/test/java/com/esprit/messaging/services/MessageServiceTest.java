package com.esprit.messaging.services;

import com.esprit.messaging.dto.MessageRequest;
import com.esprit.messaging.entities.MessageEntity;
import com.esprit.messaging.repositories.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageService service;

    // ─── createMessage() ──────────────────────────────────────────────────────

    @Test
    void createMessage_nullSenderId_throwsIllegalArgument() {
        MessageRequest req = request(null, 2L, "hello");
        assertThatThrownBy(() -> service.createMessage(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senderId");
    }

    @Test
    void createMessage_nullReceiverId_throwsIllegalArgument() {
        MessageRequest req = request(1L, null, "hello");
        assertThatThrownBy(() -> service.createMessage(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receiverId");
    }

    @Test
    void createMessage_blankContent_throwsIllegalArgument() {
        MessageRequest req = request(1L, 2L, "   ");
        assertThatThrownBy(() -> service.createMessage(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void createMessage_nullContent_throwsIllegalArgument() {
        MessageRequest req = request(1L, 2L, null);
        assertThatThrownBy(() -> service.createMessage(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void createMessage_valid_savesMessageWithTrimmedContent() {
        MessageRequest req = request(1L, 2L, "  hello world  ");
        MessageEntity saved = new MessageEntity();
        saved.setSenderId(1L);
        saved.setReceiverId(2L);
        saved.setContent("hello world");

        when(messageRepository.save(any())).thenReturn(saved);

        MessageEntity result = service.createMessage(req);

        ArgumentCaptor<MessageEntity> captor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("hello world");
        assertThat(result.getSenderId()).isEqualTo(1L);
        assertThat(result.getReceiverId()).isEqualTo(2L);
    }

    @Test
    void createMessage_valid_returnsSavedEntity() {
        MessageRequest req = request(3L, 4L, "test");
        MessageEntity saved = new MessageEntity();
        saved.setSenderId(3L);
        saved.setReceiverId(4L);
        saved.setContent("test");
        when(messageRepository.save(any())).thenReturn(saved);

        assertThat(service.createMessage(req)).isSameAs(saved);
    }

    // ─── conversationMessages() ───────────────────────────────────────────────

    @Test
    void conversationMessages_nullUserId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.conversationMessages(null, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void conversationMessages_nullPeerId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.conversationMessages(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("peerId");
    }

    @Test
    void conversationMessages_valid_delegatesToRepository() {
        MessageEntity m1 = new MessageEntity();
        MessageEntity m2 = new MessageEntity();
        when(messageRepository
                .findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByTimestampAsc(
                        1L, 2L, 2L, 1L))
                .thenReturn(List.of(m1, m2));

        List<MessageEntity> result = service.conversationMessages(1L, 2L);

        assertThat(result).containsExactly(m1, m2);
        verify(messageRepository)
                .findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByTimestampAsc(
                        1L, 2L, 2L, 1L);
    }

    @Test
    void conversationMessages_valid_emptyResult_returnsEmpty() {
        when(messageRepository
                .findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByTimestampAsc(
                        5L, 6L, 6L, 5L))
                .thenReturn(List.of());

        assertThat(service.conversationMessages(5L, 6L)).isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static MessageRequest request(Long senderId, Long receiverId, String content) {
        MessageRequest r = new MessageRequest();
        r.setSenderId(senderId);
        r.setReceiverId(receiverId);
        r.setContent(content);
        return r;
    }
}
