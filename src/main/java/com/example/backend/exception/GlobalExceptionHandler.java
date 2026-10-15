package com.example.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

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
}
