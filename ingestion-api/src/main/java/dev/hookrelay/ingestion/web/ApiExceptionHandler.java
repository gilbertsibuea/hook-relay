package dev.hookrelay.ingestion.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.hookrelay.common.security.WebhookUrlValidationException;
import dev.hookrelay.ingestion.apikey.ApiKeyNotFoundException;
import dev.hookrelay.ingestion.endpoint.EndpointNotFoundException;
import dev.hookrelay.ingestion.endpoint.InvalidEndpointRequestException;
import dev.hookrelay.ingestion.event.IdempotencyConflictException;
import dev.hookrelay.ingestion.event.InvalidEventRequestException;
import dev.hookrelay.ingestion.event.PayloadTooLargeException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({EndpointNotFoundException.class, ApiKeyNotFoundException.class})
    ResponseEntity<ProblemBody> handleNotFound(RuntimeException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://hookrelay.dev/problems/not-found",
                "Not Found",
                ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ProblemBody> handleConflict(IdempotencyConflictException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "https://hookrelay.dev/problems/conflict",
                "Conflict",
                ex.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ProblemBody> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "https://hookrelay.dev/problems/payload-too-large",
                "Payload Too Large",
                ex.getMessage());
    }

    @ExceptionHandler({
            InvalidEndpointRequestException.class,
            InvalidEventRequestException.class,
            WebhookUrlValidationException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ProblemBody> handleBadRequest(RuntimeException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://hookrelay.dev/problems/bad-request",
                "Bad Request",
                ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://hookrelay.dev/problems/bad-request",
                "Bad Request",
                "Malformed JSON request body");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemBody> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://hookrelay.dev/problems/bad-request",
                "Bad Request",
                detail);
    }

    private static ResponseEntity<ProblemBody> problem(
            HttpStatus status,
            String type,
            String title,
            String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemBody(type, title, status.value(), detail));
    }

    public record ProblemBody(String type, String title, int status, String detail) {
    }
}
