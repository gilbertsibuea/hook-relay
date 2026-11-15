package dev.hookrelay.ingestion.endpoint;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookrelay.common.security.AesGcmSecretEncryptor;
import dev.hookrelay.common.security.EncryptedSecret;
import dev.hookrelay.common.security.WebhookUrlValidationException;
import dev.hookrelay.common.security.WebhookUrlValidator;
import dev.hookrelay.ingestion.config.SsrfProperties;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final EndpointSubscriptionRepository subscriptionRepository;
    private final AesGcmSecretEncryptor secretEncryptor;
    private final WebhookUrlValidator webhookUrlValidator;
    private final SsrfProperties ssrfProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public EndpointService(
            EndpointRepository endpointRepository,
            EndpointSubscriptionRepository subscriptionRepository,
            AesGcmSecretEncryptor secretEncryptor,
            WebhookUrlValidator webhookUrlValidator,
            SsrfProperties ssrfProperties,
            SecureRandom secureRandom,
            Clock clock) {
        this.endpointRepository = endpointRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.secretEncryptor = secretEncryptor;
        this.webhookUrlValidator = webhookUrlValidator;
        this.ssrfProperties = ssrfProperties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public CreatedEndpoint create(
            UUID tenantId,
            String name,
            String url,
            List<String> subscribedEventTypes) {
        String normalizedName = requireName(name);
        String normalizedUrl = requireUrl(url);
        List<String> eventTypes = normalizeEventTypes(subscribedEventTypes);

        Instant now = Instant.now(clock);
        UUID endpointId = UUID.randomUUID();
        String rawSigningSecret = AesGcmSecretEncryptor.generateSigningSecret(secureRandom);
        EncryptedSecret encrypted = secretEncryptor.encrypt(rawSigningSecret);

        EndpointEntity endpoint = new EndpointEntity(
                endpointId,
                tenantId,
                normalizedName,
                normalizedUrl,
                EndpointStatus.ACTIVE,
                encrypted.ciphertext(),
                encrypted.nonce(),
                encrypted.keyId(),
                now,
                now,
                null);
        endpointRepository.save(endpoint);
        replaceSubscriptions(endpointId, tenantId, eventTypes, now);

        return new CreatedEndpoint(toView(endpoint, eventTypes), rawSigningSecret);
    }

    @Transactional(readOnly = true)
    public List<EndpointView> list(UUID tenantId) {
        List<EndpointEntity> endpoints =
                endpointRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
        if (endpoints.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<String>> subscriptionsByEndpoint = loadSubscriptions(endpoints.stream()
                .map(EndpointEntity::getId)
                .toList());

        return endpoints.stream()
                .map(endpoint -> toView(
                        endpoint,
                        subscriptionsByEndpoint.getOrDefault(endpoint.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EndpointView get(UUID tenantId, UUID endpointId) {
        EndpointEntity endpoint = requireOwnedEndpoint(tenantId, endpointId);
        List<String> eventTypes = subscriptionRepository
                .findByEndpointIdOrderByEventTypeAsc(endpointId)
                .stream()
                .map(EndpointSubscriptionEntity::getEventType)
                .toList();
        return toView(endpoint, eventTypes);
    }

    @Transactional
    public EndpointView update(
            UUID tenantId,
            UUID endpointId,
            String name,
            String url,
            List<String> subscribedEventTypes,
            EndpointStatus status) {
        EndpointEntity endpoint = requireOwnedEndpoint(tenantId, endpointId);
        Instant now = Instant.now(clock);

        if (name != null) {
            endpoint.rename(requireName(name), now);
        }
        if (url != null) {
            endpoint.changeUrl(requireUrl(url), now);
        }
        if (status != null) {
            if (status == EndpointStatus.DISABLED) {
                throw new InvalidEndpointRequestException(
                        "status DISABLED is only set via DELETE /v1/endpoints/{id}");
            }
            endpoint.changeStatus(status, now);
        }

        List<String> eventTypes;
        if (subscribedEventTypes != null) {
            eventTypes = normalizeEventTypes(subscribedEventTypes);
            replaceSubscriptions(endpointId, tenantId, eventTypes, now);
            endpoint.changeStatus(endpoint.getStatus(), now);
        } else {
            eventTypes = subscriptionRepository
                    .findByEndpointIdOrderByEventTypeAsc(endpointId)
                    .stream()
                    .map(EndpointSubscriptionEntity::getEventType)
                    .toList();
        }

        endpointRepository.save(endpoint);
        return toView(endpoint, eventTypes);
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID endpointId) {
        EndpointEntity endpoint = requireOwnedEndpoint(tenantId, endpointId);
        endpoint.deactivate(Instant.now(clock));
        endpointRepository.save(endpoint);
    }

    private EndpointEntity requireOwnedEndpoint(UUID tenantId, UUID endpointId) {
        return endpointRepository
                .findByIdAndTenantIdAndDeletedAtIsNull(endpointId, tenantId)
                .orElseThrow(() -> new EndpointNotFoundException(endpointId));
    }

    private void replaceSubscriptions(
            UUID endpointId,
            UUID tenantId,
            List<String> eventTypes,
            Instant createdAt) {
        subscriptionRepository.deleteByEndpointId(endpointId);
        List<EndpointSubscriptionEntity> rows = new ArrayList<>(eventTypes.size());
        for (String eventType : eventTypes) {
            rows.add(new EndpointSubscriptionEntity(
                    UUID.randomUUID(),
                    endpointId,
                    tenantId,
                    eventType,
                    createdAt));
        }
        subscriptionRepository.saveAll(rows);
    }

    private Map<UUID, List<String>> loadSubscriptions(List<UUID> endpointIds) {
        return subscriptionRepository.findByEndpointIdInOrderByEventTypeAsc(endpointIds).stream()
                .collect(Collectors.groupingBy(
                        EndpointSubscriptionEntity::getEndpointId,
                        Collectors.mapping(EndpointSubscriptionEntity::getEventType, Collectors.toList())));
    }

    private static EndpointView toView(EndpointEntity endpoint, List<String> eventTypes) {
        return new EndpointView(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getStatus(),
                List.copyOf(eventTypes),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidEndpointRequestException("name must not be blank");
        }
        return name.trim();
    }

    private String requireUrl(String url) {
        try {
            return webhookUrlValidator.validate(url, ssrfProperties.isAllowHttp());
        } catch (WebhookUrlValidationException ex) {
            throw new InvalidEndpointRequestException(ex.getReason());
        }
    }

    private static List<String> normalizeEventTypes(List<String> subscribedEventTypes) {
        if (subscribedEventTypes == null || subscribedEventTypes.isEmpty()) {
            throw new InvalidEndpointRequestException("subscribedEventTypes must not be empty");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String eventType : subscribedEventTypes) {
            if (eventType == null || eventType.isBlank()) {
                throw new InvalidEndpointRequestException("subscribedEventTypes must not contain blank values");
            }
            unique.add(eventType.trim());
        }
        return List.copyOf(unique);
    }

    public record EndpointView(
            UUID id,
            String name,
            String url,
            EndpointStatus status,
            List<String> subscribedEventTypes,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreatedEndpoint(EndpointView endpoint, String signingSecret) {
        @Override
        public String toString() {
            return "CreatedEndpoint[endpoint=" + endpoint + ", signingSecret=REDACTED]";
        }
    }
}
