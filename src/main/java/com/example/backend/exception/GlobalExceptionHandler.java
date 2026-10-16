package com.example.backend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Error body format for the frontend:
 *  - validation (400):  { "field": "message", ... }
 *  - everything else:   { "message": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler({UserAlreadyExistsException.class, InvalidCredentialsException.class})
    public ResponseEntity<Map<String, String>> handleAuthExceptions(RuntimeException ex) {
        HttpStatus status = ex instanceof UserAlreadyExistsException
                ? HttpStatus.CONFLICT
                : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(Map.of("message", ex.getMessage()));
    }

    // Errors thrown from services, e.g. new ResponseStatusException(CONFLICT, "Room is full")
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null
                ? ex.getReason()
                : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    // Unique constraint hit by two parallel requests (e.g. same email registered twice)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "The request conflicts with existing data"));
    }

    // @Version check failed: someone else changed the same room/game first
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "The data was changed by another request. Please try again."));
    }
}
