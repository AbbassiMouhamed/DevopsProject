package com.esprit.adaptivelearning.adaptive.service;

import com.esprit.adaptivelearning.entities.enums.CourseLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseLevelAccessPolicyTest {

    private CourseLevelAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new CourseLevelAccessPolicy();
    }

    @Test
    void canAccessCourse_sameLevel_returnsTrue() {
        assertTrue(policy.canAccessCourse(CourseLevel.B1, CourseLevel.B1));
    }

    @Test
    void canAccessCourse_courseLevelBelowStudentLevel_returnsTrue() {
        // Student is B2, course is A1 — lower course, should be accessible
        assertTrue(policy.canAccessCourse(CourseLevel.B2, CourseLevel.A1));
    }

    @Test
    void canAccessCourse_courseLevelAboveStudentLevel_returnsFalse() {
        // Student is A1, course is B1 — too advanced
        assertFalse(policy.canAccessCourse(CourseLevel.A1, CourseLevel.B1));
    }

    @Test
    void canAccessCourse_nullStudentLevel_returnsFalse() {
        assertFalse(policy.canAccessCourse(null, CourseLevel.A1));
    }

    @Test
    void canAccessCourse_nullCourseLevel_returnsFalse() {
        assertFalse(policy.canAccessCourse(CourseLevel.A1, null));
    }

    @Test
    void canAccessCourse_bothNull_returnsFalse() {
        assertFalse(policy.canAccessCourse(null, null));
    }

    @Test
    void canAccessCourse_lowestLevel_accessesOwnLevel() {
        assertTrue(policy.canAccessCourse(CourseLevel.A1, CourseLevel.A1));
    }

    @Test
    void canAccessCourse_highestLevel_accessesAllLevels() {
        for (CourseLevel level : CourseLevel.values()) {
            assertTrue(policy.canAccessCourse(CourseLevel.C2, level),
                    "C2 student should access " + level);
        }
    }

    @Test
    void canAccessCourse_lowestLevel_cannotAccessHigher() {
        for (CourseLevel level : new CourseLevel[]{CourseLevel.A2, CourseLevel.B1, CourseLevel.B2, CourseLevel.C1, CourseLevel.C2}) {
            assertFalse(policy.canAccessCourse(CourseLevel.A1, level),
                    "A1 student should NOT access " + level);
        }
    }
}
