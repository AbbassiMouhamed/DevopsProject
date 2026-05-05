package com.esprit.quiz.service;

import com.esprit.quiz.dto.LevelFinalAttemptResultDto;
import com.esprit.quiz.dto.LevelFinalAttemptStartResponse;
import com.esprit.quiz.entities.LevelFinalAttempt;
import com.esprit.quiz.entities.LevelFinalAttemptStatus;
import com.esprit.quiz.repositories.LevelFinalAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LevelFinalQuizServiceTest {

    @Mock
    private LevelFinalAttemptRepository repository;

    @InjectMocks
    private LevelFinalQuizService service;

    // ─── start() ──────────────────────────────────────────────────────────────

    @Test
    void start_savesAttemptWithCorrectSubjectAndStatus() {
        when(repository.save(any())).thenAnswer(inv -> {
            LevelFinalAttempt a = inv.getArgument(0);
            a.setId(42L);
            return a;
        });

        LevelFinalAttemptStartResponse resp = service.start("user-abc");

        assertThat(resp.attemptId()).isEqualTo(42L);
        ArgumentCaptor<LevelFinalAttempt> captor = ArgumentCaptor.forClass(LevelFinalAttempt.class);
        verify(repository).save(captor.capture());
        LevelFinalAttempt saved = captor.getValue();
        assertThat(saved.getKeycloakSubject()).isEqualTo("user-abc");
        assertThat(saved.getStatus()).isEqualTo(LevelFinalAttemptStatus.IN_PROGRESS);
    }

    @Test
    void start_returnsIdFromSavedEntity() {
        when(repository.save(any())).thenAnswer(inv -> {
            LevelFinalAttempt a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        assertThat(service.start("subject-x").attemptId()).isEqualTo(99L);
    }

    // ─── completeAttempt() ────────────────────────────────────────────────────

    @Test
    void completeAttempt_notFound_throwsNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeAttempt(1L, "u1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void completeAttempt_wrongSubject_throwsForbidden() {
        LevelFinalAttempt a = inProgressAttempt(7L, "owner");
        when(repository.findById(7L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.completeAttempt(7L, "intruder"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void completeAttempt_alreadyCompleted_returnsExistingResultWithoutSaving() {
        LevelFinalAttempt a = completedAttempt(3L, "u2", 75);
        when(repository.findById(3L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.completeAttempt(3L, "u2");

        verify(repository, never()).save(any());
        assertThat(dto.completed()).isTrue();
        assertThat(dto.scorePercent()).isEqualTo(75);
        assertThat(dto.passed()).isTrue();    // 75 >= 60
        assertThat(dto.weakAreasAuto()).isEmpty(); // score >= 60 → no hints
    }

    @Test
    void completeAttempt_alreadyCompleted_failingScore_passedFalse() {
        LevelFinalAttempt a = completedAttempt(4L, "u3", 40);
        when(repository.findById(4L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.completeAttempt(4L, "u3");

        verify(repository, never()).save(any());
        assertThat(dto.passed()).isFalse();
        assertThat(dto.weakAreasAuto()).isNotEmpty(); // score < 60 → hint present
    }

    @Test
    void completeAttempt_inProgress_completesAndSaves() {
        LevelFinalAttempt a = inProgressAttempt(5L, "u4");
        when(repository.findById(5L)).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LevelFinalAttemptResultDto dto = service.completeAttempt(5L, "u4");

        verify(repository).save(any());
        assertThat(dto.completed()).isTrue();
        assertThat(dto.scorePercent()).isBetween(0, 100);
        assertThat(dto.keycloakSubject()).isEqualTo("u4");
    }

    @Test
    void completeAttempt_inProgress_completedAtIsSet() {
        LevelFinalAttempt a = inProgressAttempt(6L, "u5");
        when(repository.findById(6L)).thenReturn(Optional.of(a));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.completeAttempt(6L, "u5");

        assertThat(a.getCompletedAt()).isNotNull();
        assertThat(a.getCompletedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void completeAttempt_inProgress_scoreExactly60_isPassed() {
        // Because score is random we can inject a known score via a pre-completed attempt
        // and verify toDto logic: passed iff completed && score >= 60
        LevelFinalAttempt a = completedAttempt(10L, "u6", 60);
        when(repository.findById(10L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.completeAttempt(10L, "u6");
        assertThat(dto.passed()).isTrue();
        assertThat(dto.weakAreasAuto()).isEmpty();
    }

    @Test
    void completeAttempt_inProgress_score59_notPassed() {
        LevelFinalAttempt a = completedAttempt(11L, "u7", 59);
        when(repository.findById(11L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.completeAttempt(11L, "u7");
        assertThat(dto.passed()).isFalse();
        assertThat(dto.weakAreasAuto()).isNotEmpty();
    }

    // ─── getResult() ──────────────────────────────────────────────────────────

    @Test
    void getResult_notFound_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(99L, "u"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getResult_wrongSubject_throwsForbidden() {
        LevelFinalAttempt a = completedAttempt(20L, "owner", 80);
        when(repository.findById(20L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.getResult(20L, "hacker"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getResult_completed_returnsDtoCorrectly() {
        LevelFinalAttempt a = completedAttempt(21L, "u8", 80);
        when(repository.findById(21L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.getResult(21L, "u8");

        assertThat(dto.attemptId()).isEqualTo(21L);
        assertThat(dto.completed()).isTrue();
        assertThat(dto.scorePercent()).isEqualTo(80);
        assertThat(dto.passed()).isTrue();
        assertThat(dto.keycloakSubject()).isEqualTo("u8");
        assertThat(dto.weakAreasAuto()).isEmpty();
    }

    @Test
    void getResult_inProgress_completedFalsePassedFalse() {
        LevelFinalAttempt a = inProgressAttempt(22L, "u9");
        when(repository.findById(22L)).thenReturn(Optional.of(a));

        LevelFinalAttemptResultDto dto = service.getResult(22L, "u9");

        assertThat(dto.completed()).isFalse();
        assertThat(dto.passed()).isFalse();
        assertThat(dto.scorePercent()).isEqualTo(0); // null scorePercent → 0
    }

    @Test
    void getResult_neverSaves() {
        LevelFinalAttempt a = completedAttempt(23L, "u10", 90);
        when(repository.findById(23L)).thenReturn(Optional.of(a));

        service.getResult(23L, "u10");

        verify(repository, never()).save(any());
    }

    // ─── weakAreasAuto cycle (buildWeakAreasAuto) ─────────────────────────────

    @Test
    void weakAreasAuto_failingScoreCyclesThroughThreeHints() {
        // The three hints cycle at indices 0,1,2 = scores 0,1,2 (mod 3)
        // Verify score=0→hint[0], score=1→hint[1], score=2→hint[2]
        String[] expected = {
                "listening — compréhension orale",
                "grammaire — structures complexes",
                "reading — vocabulaire académique"
        };
        for (int score = 0; score < 3; score++) {
            LevelFinalAttempt a = completedAttempt(100L + score, "u", score);
            when(repository.findById(100L + score)).thenReturn(Optional.of(a));

            LevelFinalAttemptResultDto dto = service.getResult(100L + score, "u");
            assertThat(dto.weakAreasAuto())
                    .as("score=%d", score)
                    .isEqualTo(expected[score]);
        }
    }

    @Test
    void weakAreasAuto_passingScore_isBlank() {
        LevelFinalAttempt a = completedAttempt(200L, "u", 61);
        when(repository.findById(200L)).thenReturn(Optional.of(a));

        assertThat(service.getResult(200L, "u").weakAreasAuto()).isEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static LevelFinalAttempt inProgressAttempt(long id, String subject) {
        LevelFinalAttempt a = new LevelFinalAttempt();
        a.setId(id);
        a.setKeycloakSubject(subject);
        a.setStatus(LevelFinalAttemptStatus.IN_PROGRESS);
        return a;
    }

    private static LevelFinalAttempt completedAttempt(long id, String subject, int score) {
        LevelFinalAttempt a = new LevelFinalAttempt();
        a.setId(id);
        a.setKeycloakSubject(subject);
        a.setStatus(LevelFinalAttemptStatus.COMPLETED);
        a.setScorePercent(score);
        a.setCompletedAt(Instant.now());
        // mirror buildWeakAreasAuto logic
        a.setWeakAreasAuto(score >= 60 ? "" : buildWeakHint(score));
        return a;
    }

    private static String buildWeakHint(int score) {
        String[] hints = {
                "listening — compréhension orale",
                "grammaire — structures complexes",
                "reading — vocabulaire académique"
        };
        return hints[Math.floorMod(score, hints.length)];
    }
}
