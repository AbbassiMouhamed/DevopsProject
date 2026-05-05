package com.esprit.courses.exceptions;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalValidationExceptionHandlerTest {

    private final GlobalValidationExceptionHandler handler = new GlobalValidationExceptionHandler();

    // ─── MethodArgumentNotValidException ──────────────────────────────────────

    @Test
    void handleMethodArgumentNotValid_returns400() {
        MethodArgumentNotValidException ex = mockMethodArgumentEx(List.of());
        ResponseEntity<Map<String, Object>> response = handler.handleMethodArgumentNotValid(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMethodArgumentNotValid_bodyContainsStatus400() {
        MethodArgumentNotValidException ex = mockMethodArgumentEx(List.of());
        Map<String, Object> body = handler.handleMethodArgumentNotValid(ex).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(400);
    }

    @Test
    void handleMethodArgumentNotValid_bodyContainsErrorMessage() {
        MethodArgumentNotValidException ex = mockMethodArgumentEx(List.of());
        Map<String, Object> body = handler.handleMethodArgumentNotValid(ex).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation failed");
    }

    @Test
    void handleMethodArgumentNotValid_includesFieldErrors() {
        FieldError fe = new FieldError("obj", "title", "must not be blank");
        MethodArgumentNotValidException ex = mockMethodArgumentEx(List.of(fe));

        Map<String, Object> body = handler.handleMethodArgumentNotValid(ex).getBody();
        assertThat(body).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) body.get("validationErrors");
        assertThat(fieldErrors).containsEntry("title", "must not be blank");
    }

    @Test
    void handleMethodArgumentNotValid_multipleFieldErrors_allIncluded() {
        FieldError e1 = new FieldError("obj", "name", "required");
        FieldError e2 = new FieldError("obj", "email", "invalid format");
        MethodArgumentNotValidException ex = mockMethodArgumentEx(List.of(e1, e2));

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>)
                handler.handleMethodArgumentNotValid(ex).getBody().get("validationErrors");
        assertThat(fieldErrors)
                .containsEntry("name", "required")
                .containsEntry("email", "invalid format");
    }

    // ─── ConstraintViolationException ─────────────────────────────────────────

    @Test
    void handleConstraintViolation_returns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolationException ex = new ConstraintViolationException("size constraint violated", Set.of());
        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleConstraintViolation_bodyContainsStatus400() {
        ConstraintViolationException ex = new ConstraintViolationException("length must be >= 3", Set.of());
        Map<String, Object> body = handler.handleConstraintViolation(ex).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("error")).isEqualTo("Validation failed");
    }

    @Test
    void handleConstraintViolation_bodyContainsMessage() {
        ConstraintViolationException ex = new ConstraintViolationException("price must be positive", Set.of());
        Map<String, Object> body = handler.handleConstraintViolation(ex).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("price must be positive");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static MethodArgumentNotValidException mockMethodArgumentEx(List<FieldError> fieldErrors) {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(fieldErrors);
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        return ex;
    }
}
