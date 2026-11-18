package dev.hookrelay.ingestion.event;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.hookrelay.ingestion.config.IngestionProperties;
import dev.hookrelay.ingestion.outbox.OutboxEntity;
import dev.hookrelay.ingestion.outbox.OutboxRepository;

@Service
public class EventIngestionService {

    private final EventRepository eventRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxRepository outboxRepository;
    private final RequestHashCalculator requestHashCalculator;
    private final IngestionProperties ingestionProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public EventIngestionService(
            EventRepository eventRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            OutboxRepository outboxRepository,
            RequestHashCalculator requestHashCalculator,
            IngestionProperties ingestionProperties,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.outboxRepository = outboxRepository;
        this.requestHashCalculator = requestHashCalculator;
        this.ingestionProperties = ingestionProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public AcceptedEvent accept(
            UUID tenantId,
            String idempotencyKey,
            String eventType,
            JsonNode payload) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        String normalizedType = requireEventType(eventType);
        JsonNode normalizedPayload = requirePayload(payload);
        enforcePayloadSize(normalizedPayload);

        String requestHash = requestHashCalculator.hash(normalizedType, normalizedPayload);

        return idempotencyKeyRepository
                .findByTenantIdAndIdempotencyKey(tenantId, normalizedKey)
                .map(existing -> replayOrConflict(existing, requestHash))
                .orElseGet(() -> createOrReplayRace(
                        tenantId, normalizedKey, normalizedType, normalizedPayload, requestHash));
    }

    /**
     * Inserts event + idempotency + outbox in one transaction. On a concurrent unique-key
     * collision Postgres aborts that transaction; we roll it back fully, then replay from a
     * fresh lookup outside the failed TX.
     */
    private AcceptedEvent createOrReplayRace(
            UUID tenantId,
            String idempotencyKey,
            String eventType,
            JsonNode payload,
            String requestHash) {
        try {
            return transactionTemplate.execute(status ->
                    createNew(tenantId, idempotencyKey, eventType, payload, requestHash));
        } catch (DataIntegrityViolationException ex) {
            return idempotencyKeyRepository
                    .findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .map(existing -> replayOrConflict(existing, requestHash))
                    .orElseThrow(() -> ex);
        }
    }

    private AcceptedEvent createNew(
            UUID tenantId,
            String idempotencyKey,
            String eventType,
            JsonNode payload,
            String requestHash) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(ingestionProperties.getEventRetentionDays(), ChronoUnit.DAYS);
        UUID eventId = UUID.randomUUID();
        String payloadJson = writeJson(payload);

        EventEntity event = new EventEntity(
                eventId,
                tenantId,
                eventType,
                payloadJson,
                EventStatus.ACCEPTED,
                now,
                expiresAt);

        IdempotencyKeyEntity idempotency = new IdempotencyKeyEntity(
                UUID.randomUUID(),
                tenantId,
                idempotencyKey,
                requestHash,
                eventId,
                now,
                expiresAt);

        String outboxPayload = writeJson(outboxEnvelope(event));
        OutboxEntity outbox = new OutboxEntity(
                OutboxEntity.AGGREGATE_EVENT,
                eventId,
                OutboxEntity.MESSAGE_EVENT_ACCEPTED,
                outboxPayload,
                now);

        eventRepository.saveAndFlush(event);
        idempotencyKeyRepository.saveAndFlush(idempotency);
        outboxRepository.saveAndFlush(outbox);
        return AcceptedEvent.from(event);
    }

    private AcceptedEvent replayOrConflict(IdempotencyKeyEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with a different request payload");
        }
        EventEntity event = eventRepository
                .findById(existing.getEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency record references missing event " + existing.getEventId()));
        return AcceptedEvent.from(event);
    }

    private ObjectNode outboxEnvelope(EventEntity event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("eventId", event.getId().toString());
        node.put("tenantId", event.getTenantId().toString());
        node.put("type", event.getEventType());
        node.put("status", event.getStatus().name().toLowerCase());
        node.put("createdAt", event.getCreatedAt().toString());
        node.set("payload", readJson(event.getPayloadJson()));
        return node;
    }

    private void enforcePayloadSize(JsonNode payload) {
        int size = writeJson(payload).getBytes(StandardCharsets.UTF_8).length;
        int max = ingestionProperties.getMaxPayloadBytes();
        if (size > max) {
            throw new PayloadTooLargeException(size, max);
        }
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidEventRequestException("Idempotency-Key header is required");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 256) {
            throw new InvalidEventRequestException("Idempotency-Key must be at most 256 characters");
        }
        return trimmed;
    }

    private static String requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidEventRequestException("type must not be blank");
        }
        String trimmed = eventType.trim();
        if (trimmed.length() > 200) {
            throw new InvalidEventRequestException("type must be at most 200 characters");
        }
        return trimmed;
    }

    private static JsonNode requirePayload(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            throw new InvalidEventRequestException("payload is required");
        }
        if (!payload.isObject() && !payload.isArray()) {
            throw new InvalidEventRequestException("payload must be a JSON object or array");
        }
        return payload;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException ex) {
            throw new InvalidEventRequestException("payload could not be serialized");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("stored payload is not valid JSON", ex);
        }
    }

    public record AcceptedEvent(UUID eventId, EventStatus status, Instant createdAt) {
        static AcceptedEvent from(EventEntity event) {
            return new AcceptedEvent(event.getId(), event.getStatus(), event.getCreatedAt());
        }
    }
}
