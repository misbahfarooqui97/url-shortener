package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import com.charlesschwab.urlshortener.exception.ShortCodeGenerationException;
import com.charlesschwab.urlshortener.exception.ShortUrlNotFoundException;
import com.charlesschwab.urlshortener.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Objects;

/**
 * Translates service-layer and validation exceptions into the single documented error
 * shape (see the "Error format" section of {@code docs/specs/api-specification.md}).
 * This is the only place in the application that maps a failure to an HTTP status code,
 * and it never forwards an exception message, class name, or stack trace that was not
 * already written to be safely user-facing (NFR-4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Validation failed"))
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShortUrlNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGenerationFailure(ShortCodeGenerationException ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI(), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
