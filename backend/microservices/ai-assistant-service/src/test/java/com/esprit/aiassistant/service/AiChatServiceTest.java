package com.esprit.aiassistant.service;

import com.esprit.aiassistant.dto.ChatResponse;
import com.esprit.aiassistant.entity.AiConversation;
import com.esprit.aiassistant.repository.AiConversationRepository;
import com.esprit.aiassistant.repository.AiMessageRepository;
import com.esprit.aiassistant.repository.ChatHistoryMirrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private AiConversationRepository conversationRepository;

    @Mock
    private AiMessageRepository messageRepository;

    @Mock
    private ChatHistoryMirrorRepository chatHistoryMirrorRepository;

    @Mock
    private GeminiService geminiService;

    @InjectMocks
    private AiChatService service;

    // ─── requireUser() ────────────────────────────────────────────────────────

    @Test
    void createConversation_nullUserId_throwsSecurityException() {
        assertThatThrownBy(() -> service.createConversation(null, "title"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void createConversation_zeroUserId_throwsSecurityException() {
        assertThatThrownBy(() -> service.createConversation(0L, "title"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createConversation_negativeUserId_throwsSecurityException() {
        assertThatThrownBy(() -> service.createConversation(-1L, "title"))
                .isInstanceOf(SecurityException.class);
    }

    // ─── createConversation() ─────────────────────────────────────────────────

    @Test
    void createConversation_validTitle_savesConversationWithTitle() {
        AiConversation saved = new AiConversation();
        saved.setId(1L);
        saved.setUserId(5L);
        saved.setTitle("My talk");
        when(conversationRepository.save(any())).thenReturn(saved);

        AiConversation result = service.createConversation(5L, "My talk");

        ArgumentCaptor<AiConversation> captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("My talk");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void createConversation_nullTitle_defaultsToNewConversation() {
        AiConversation saved = new AiConversation();
        saved.setId(2L);
        when(conversationRepository.save(any())).thenReturn(saved);

        service.createConversation(1L, null);

        ArgumentCaptor<AiConversation> captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New conversation");
    }

    @Test
    void createConversation_blankTitle_defaultsToNewConversation() {
        AiConversation saved = new AiConversation();
        when(conversationRepository.save(any())).thenReturn(saved);

        service.createConversation(1L, "   ");

        ArgumentCaptor<AiConversation> captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New conversation");
    }

    // ─── myConversations() ────────────────────────────────────────────────────

    @Test
    void myConversations_validUser_returnsRepositoryResult() {
        AiConversation c1 = new AiConversation();
        AiConversation c2 = new AiConversation();
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(3L))
                .thenReturn(List.of(c1, c2));

        List<AiConversation> result = service.myConversations(3L);

        assertThat(result).containsExactly(c1, c2);
    }

    @Test
    void myConversations_invalidUser_throwsSecurityException() {
        assertThatThrownBy(() -> service.myConversations(null))
                .isInstanceOf(SecurityException.class);
    }

    // ─── deleteConversation() ─────────────────────────────────────────────────

    @Test
    void deleteConversation_notFound_throwsIllegalArgument() {
        when(conversationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation(10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void deleteConversation_found_deletesConversation() {
        AiConversation c = new AiConversation();
        c.setId(5L);
        when(conversationRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.of(c));

        service.deleteConversation(5L, 2L);

        verify(conversationRepository).delete(c);
    }

    // ─── deleteAllMyConversations() ───────────────────────────────────────────

    @Test
    void deleteAllMyConversations_invalidUser_throwsSecurityException() {
        assertThatThrownBy(() -> service.deleteAllMyConversations(null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void deleteAllMyConversations_validUser_returnsCountFromRepo() {
        when(conversationRepository.deleteByUserId(7L)).thenReturn(3L);

        long count = service.deleteAllMyConversations(7L);

        assertThat(count).isEqualTo(3L);
    }

    // ─── chat() ───────────────────────────────────────────────────────────────

    @Test
    void chat_blankMessage_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.chat("  ", null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message is required");
    }

    @Test
    void chat_newConversation_createsConversationAndReturnsReply() {
        AiConversation conv = new AiConversation();
        conv.setId(1L);
        conv.setTitle("How are you");
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());

        when(conversationRepository.save(any())).thenReturn(conv);
        when(geminiService.generateEnglishTeacherReply(any())).thenReturn("I am fine");
        when(messageRepository.save(any())).thenReturn(null);
        when(chatHistoryMirrorRepository.save(any())).thenReturn(null);

        ChatResponse result = service.chat("How are you", null, 1L);

        assertThat(result.getReply()).isEqualTo("I am fine");
        assertThat(result.getConversationId()).isEqualTo(1L);
    }
}
