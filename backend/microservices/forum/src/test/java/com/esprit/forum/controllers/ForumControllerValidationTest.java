package com.esprit.forum.controllers;

import com.esprit.forum.entities.ForumPost;
import com.esprit.forum.repositories.ForumPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ForumController's validation logic via updatePost (no auth param).
 */
@ExtendWith(MockitoExtension.class)
class ForumControllerValidationTest {

    @Mock
    private ForumPostRepository forumPostRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private ForumController controller;

    @BeforeEach
    void setUp() {
        // Stub RestClient.Builder chain so constructor doesn't fail
        when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        controller = new ForumController(forumPostRepository, restClientBuilder, "http://localhost:8087");
    }

    // ─── validatePostPayload via updatePost ───────────────────────────────────

    @Test
    void updatePost_nullPost_throws400() {
        assertThatThrownBy(() -> controller.updatePost(1L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_nullTitle_throws400() {
        ForumPost post = post(null, "some content");
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_blankTitle_throws400() {
        ForumPost post = post("   ", "some content");
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_titleTooShort_throws400() {
        ForumPost post = post("ab", "some content");
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_titleWithDigit_throws400() {
        ForumPost post = post("title123", "some content");
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_nullContent_throws400() {
        ForumPost post = post("Valid Title", null);
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_blankContent_throws400() {
        ForumPost post = post("Valid Title", "   ");
        assertThatThrownBy(() -> controller.updatePost(1L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updatePost_validPayload_notFoundPost_throws404() {
        ForumPost post = post("Valid Title", "Valid content here");
        when(forumPostRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updatePost(99L, post))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatePost_validPayload_existingPost_savesAndReturns() {
        ForumPost request = post("Valid Title", "Valid content here");
        ForumPost existing = post("Old Title", "Old content");
        existing.setId(1L);
        existing.setCreatedAt(LocalDateTime.now());

        when(forumPostRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(forumPostRepository.save(any())).thenReturn(existing);

        // Should not throw
        Object result = controller.updatePost(1L, request);
        assertThat(result).isNotNull();
        verify(forumPostRepository).save(existing);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static ForumPost post(String title, String content) {
        ForumPost p = new ForumPost();
        p.setTitle(title);
        p.setContent(content);
        p.setAuthor("testuser");
        return p;
    }
}
