package com.esprit.adaptivelearning.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotFoundExceptionTest {

    @Test
    void constructor_setsMessage() {
        NotFoundException ex = new NotFoundException("Resource not found");
        assertEquals("Resource not found", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        NotFoundException ex = new NotFoundException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void messagePreservedExactly() {
        String msg = "LearningPath id=42 not found";
        NotFoundException ex = new NotFoundException(msg);
        assertEquals(msg, ex.getMessage());
    }
}
