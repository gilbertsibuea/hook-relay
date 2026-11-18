package dev.hookrelay.ingestion.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

import dev.hookrelay.common.auth.TenantPrincipal;
import dev.hookrelay.ingestion.event.EventIngestionService;
import dev.hookrelay.ingestion.event.EventIngestionService.AcceptedEvent;
import dev.hookrelay.ingestion.event.EventStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/v1/events")
public class EventController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventAcceptedResponse ingest(
            Authentication authentication,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateEventRequest request) {
        TenantPrincipal tenant = (TenantPrincipal) authentication.getPrincipal();
        AcceptedEvent accepted = eventIngestionService.accept(
                tenant.tenantId(),
                idempotencyKey,
                request.type(),
                request.payload());
        return EventAcceptedResponse.from(accepted);
    }

    public record CreateEventRequest(
            @NotBlank @Size(max = 200) String type,
            @NotNull JsonNode payload) {
    }

    public record EventAcceptedResponse(UUID eventId, String status, Instant createdAt) {
        static EventAcceptedResponse from(AcceptedEvent accepted) {
            return new EventAcceptedResponse(
                    accepted.eventId(),
                    accepted.status() == EventStatus.ACCEPTED ? "accepted" : accepted.status().name().toLowerCase(),
                    accepted.createdAt());
        }
    }
}
