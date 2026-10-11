package com.moneyflow.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
            .map(this::fieldMessage).distinct().collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, "Validation failed", details, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> invalidParameter(ConstraintViolationException ex, HttpServletRequest request) {
        String details = ex.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, "Invalid request parameter", details, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed request body or unsupported field value", null, request.getRequestURI());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return response(status, ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), null, request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The operation conflicts with existing data or database constraints", null, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred", null, request.getRequestURI());
    }

    private String fieldMessage(FieldError e) { return e.getField() + ": " + (e.getDefaultMessage() == null ? "invalid value" : e.getDefaultMessage()); }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, String details, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null && !details.isBlank()) body.put("details", details);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
