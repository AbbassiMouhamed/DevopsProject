package com.esprit.adaptivelearning.adaptive.service;

import com.esprit.adaptivelearning.entities.LearningDifficultyAlert;
import com.esprit.adaptivelearning.entities.StudentLevelTestResult;
import com.esprit.adaptivelearning.entities.StudentProgress;
import com.esprit.adaptivelearning.entities.enums.CourseLevel;
import com.esprit.adaptivelearning.entities.enums.DifficultySeverity;
import com.esprit.adaptivelearning.repositories.LearningDifficultyAlertRepository;
import com.esprit.adaptivelearning.repositories.StudentLevelTestResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdaptiveDifficultyServiceTest {

    @Mock
    private LearningDifficultyAlertRepository alertRepository;

    @Mock
    private StudentLevelTestResultRepository levelTestResultRepository;

    @InjectMocks
    private AdaptiveDifficultyService service;

    // ---- analyzeProgressSnapshot ----

    @Test
    void analyzeProgressSnapshot_nullTotalItems_doesNothing() {
        StudentProgress p = progressWith(null, 0, 0.0, Instant.now());
        service.analyzeProgressSnapshot(p);
        verifyNoInteractions(alertRepository);
    }

    @Test
    void analyzeProgressSnapshot_zeroTotalItems_doesNothing() {
        StudentProgress p = progressWith(0, 0, 0.0, Instant.now());
        service.analyzeProgressSnapshot(p);
        verifyNoInteractions(alertRepository);
    }

    @Test
    void analyzeProgressSnapshot_lowProgressWithPendingItems_createsSlowAlert() {
        // 20% completion with 8 items total (6 pending)
        StudentProgress p = progressWith(8, 2, 20.0, Instant.now());
        p.setStudentId(1L);
        p.setLearningPathId(10L);
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(1L, AdaptiveDifficultyService.PREFIX_SLOW))
                .thenReturn(false);

        service.analyzeProgressSnapshot(p);

        ArgumentCaptor<LearningDifficultyAlert> captor = ArgumentCaptor.forClass(LearningDifficultyAlert.class);
        verify(alertRepository).save(captor.capture());
        LearningDifficultyAlert saved = captor.getValue();
        assertEquals(DifficultySeverity.MEDIUM, saved.getSeverity());
        assertFalse(saved.isResolved());
        assertTrue(saved.getReason().startsWith(AdaptiveDifficultyService.PREFIX_SLOW));
    }

    @Test
    void analyzeProgressSnapshot_lowProgress_alertAlreadyExists_doesNotCreateDuplicate() {
        StudentProgress p = progressWith(8, 2, 20.0, Instant.now());
        p.setStudentId(1L);
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(1L, AdaptiveDifficultyService.PREFIX_SLOW))
                .thenReturn(true);

        service.analyzeProgressSnapshot(p);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void analyzeProgressSnapshot_goodProgress_noSlowAlert() {
        // 80% completion — should not trigger SLOW alert
        StudentProgress p = progressWith(10, 8, 80.0, Instant.now());
        p.setStudentId(1L);

        service.analyzeProgressSnapshot(p);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void analyzeProgressSnapshot_inactiveForMoreThan7Days_createsInactivityAlert() {
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        // pct=50 keeps us above the 30% slow-progress threshold so only inactivity fires
        StudentProgress p = progressWith(5, 0, 50.0, tenDaysAgo);
        p.setStudentId(2L);
        p.setLearningPathId(20L);
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(2L, AdaptiveDifficultyService.PREFIX_INACTIVE))
                .thenReturn(false);

        service.analyzeProgressSnapshot(p);

        ArgumentCaptor<LearningDifficultyAlert> captor = ArgumentCaptor.forClass(LearningDifficultyAlert.class);
        verify(alertRepository).save(captor.capture());
        assertTrue(captor.getValue().getReason().startsWith(AdaptiveDifficultyService.PREFIX_INACTIVE));
        assertEquals(DifficultySeverity.LOW, captor.getValue().getSeverity());
    }

    @Test
    void analyzeProgressSnapshot_recentActivity_noInactivityAlert() {
        // completedItems > 0, so inactivity check won't fire
        StudentProgress p = progressWith(5, 1, 20.0, Instant.now().minus(1, ChronoUnit.DAYS));
        p.setStudentId(3L);
        // Low pct but only 4 pending (< 3 threshold isn't met since pending=4>=3 BUT pct=20<30 so slow alert fires)
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(3L, AdaptiveDifficultyService.PREFIX_SLOW))
                .thenReturn(true); // already exists, no save
        service.analyzeProgressSnapshot(p);
        verify(alertRepository, never()).save(any());
    }

    // ---- recordFailedLevelTest ----

    @Test
    void recordFailedLevelTest_createsHighSeverityAlert() {
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(5L, AdaptiveDifficultyService.PREFIX_FAIL_TEST))
                .thenReturn(false);

        service.recordFailedLevelTest(5L, 100L, 45);

        ArgumentCaptor<LearningDifficultyAlert> captor = ArgumentCaptor.forClass(LearningDifficultyAlert.class);
        verify(alertRepository).save(captor.capture());
        assertEquals(DifficultySeverity.HIGH, captor.getValue().getSeverity());
        assertTrue(captor.getValue().getReason().contains("45"));
        assertTrue(captor.getValue().getReason().startsWith(AdaptiveDifficultyService.PREFIX_FAIL_TEST));
    }

    @Test
    void recordFailedLevelTest_alertAlreadyExists_noDuplicate() {
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(5L, AdaptiveDifficultyService.PREFIX_FAIL_TEST))
                .thenReturn(true);

        service.recordFailedLevelTest(5L, 100L, 45);

        verify(alertRepository, never()).save(any());
    }

    // ---- flagLowProgressAfterItemUpdate ----

    @Test
    void flagLowProgressAfterItemUpdate_nullTotalItems_doesNothing() {
        StudentProgress p = progressWith(null, 0, 0.0, Instant.now());
        service.flagLowProgressAfterItemUpdate(p);
        verifyNoInteractions(alertRepository);
    }

    @Test
    void flagLowProgressAfterItemUpdate_veryLowProgressManyPending_createsHighAlert() {
        StudentProgress p = progressWith(10, 1, 10.0, Instant.now());
        p.setStudentId(6L);
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(6L, AdaptiveDifficultyService.PREFIX_SLOW))
                .thenReturn(false);

        service.flagLowProgressAfterItemUpdate(p);

        ArgumentCaptor<LearningDifficultyAlert> captor = ArgumentCaptor.forClass(LearningDifficultyAlert.class);
        verify(alertRepository).save(captor.capture());
        assertEquals(DifficultySeverity.HIGH, captor.getValue().getSeverity());
    }

    @Test
    void flagLowProgressAfterItemUpdate_fewerThan5Pending_noAlert() {
        // 4 pending (10 total, 6 completed), pct=15 < 20 but pending < 5
        StudentProgress p = progressWith(10, 6, 15.0, Instant.now());
        p.setStudentId(7L);

        service.flagLowProgressAfterItemUpdate(p);

        verify(alertRepository, never()).save(any());
    }

    // ---- scanRecentFailedTests ----

    @Test
    void scanRecentFailedTests_recentFailedTest_createsAlert() {
        StudentLevelTestResult result = new StudentLevelTestResult();
        result.setStudentId(8L);
        result.setPassed(false);
        result.setTestDate(Instant.now().minus(5, ChronoUnit.DAYS));
        result.setScore(40);
        result.setScorePercent(40);
        result.setCurrentLevel(CourseLevel.B1);
        result.setUnlockedLevel(CourseLevel.B1);

        when(levelTestResultRepository.findAll()).thenReturn(List.of(result));
        when(alertRepository.existsByStudentIdAndResolvedFalseAndReasonStartingWith(8L, AdaptiveDifficultyService.PREFIX_FAIL_TEST))
                .thenReturn(false);

        service.scanRecentFailedTests();

        verify(alertRepository).save(any(LearningDifficultyAlert.class));
    }

    @Test
    void scanRecentFailedTests_passedTest_noAlert() {
        StudentLevelTestResult result = new StudentLevelTestResult();
        result.setStudentId(9L);
        result.setPassed(true);
        result.setTestDate(Instant.now().minus(3, ChronoUnit.DAYS));
        result.setScore(80);
        result.setScorePercent(80);
        result.setCurrentLevel(CourseLevel.B1);
        result.setUnlockedLevel(CourseLevel.B2);

        when(levelTestResultRepository.findAll()).thenReturn(List.of(result));

        service.scanRecentFailedTests();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void scanRecentFailedTests_oldFailedTest_noAlert() {
        // Test was 20 days ago — outside the 14-day window
        StudentLevelTestResult result = new StudentLevelTestResult();
        result.setStudentId(10L);
        result.setPassed(false);
        result.setTestDate(Instant.now().minus(20, ChronoUnit.DAYS));
        result.setScore(30);
        result.setScorePercent(30);
        result.setCurrentLevel(CourseLevel.A2);
        result.setUnlockedLevel(CourseLevel.A2);

        when(levelTestResultRepository.findAll()).thenReturn(List.of(result));

        service.scanRecentFailedTests();

        verify(alertRepository, never()).save(any());
    }

    @Test
    void scanRecentFailedTests_emptyRepository_noInteractions() {
        when(levelTestResultRepository.findAll()).thenReturn(List.of());

        service.scanRecentFailedTests();

        verify(alertRepository, never()).save(any());
    }

    // ---- helpers ----

    private StudentProgress progressWith(Integer totalItems, Integer completedItems,
                                         Double completionPct, Instant updatedAt) {
        StudentProgress p = new StudentProgress();
        p.setTotalItems(totalItems);
        p.setCompletedItems(completedItems);
        p.setCompletionPercentage(completionPct);
        p.setUpdatedAt(updatedAt);
        p.setCurrentLevel(CourseLevel.A1);
        return p;
    }
}
