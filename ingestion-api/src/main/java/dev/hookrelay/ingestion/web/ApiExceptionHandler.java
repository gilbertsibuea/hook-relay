package dev.hookrelay.ingestion.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.hookrelay.ingestion.endpoint.EndpointNotFoundException;
import dev.hookrelay.ingestion.endpoint.InvalidEndpointRequestException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EndpointNotFoundException.class)
    ResponseEntity<ProblemBody> handleNotFound(EndpointNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://hookrelay.dev/problems/not-found",
                "Not Found",
                ex.getMessage());
    }

    @ExceptionHandler({InvalidEndpointRequestException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemBody> handleBadRequest(RuntimeException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://hookrelay.dev/problems/bad-request",
                "Bad Request",
                ex.getMessage());
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
