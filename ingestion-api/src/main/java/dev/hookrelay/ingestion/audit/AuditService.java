package dev.hookrelay.ingestion.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public static final String ACTION_API_KEY_ROTATED = "API_KEY_ROTATED";
    public static final String ACTION_API_KEY_REVOKED = "API_KEY_REVOKED";
    public static final String RESOURCE_API_KEY = "api_key";

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(
            UUID tenantId,
            UUID actorApiKeyId,
            String action,
            String resourceType,
            UUID resourceId,
            String detailJson) {
        auditEventRepository.save(new AuditEventEntity(
                tenantId,
                actorApiKeyId,
                action,
                resourceType,
                resourceId,
                detailJson,
                Instant.now(clock)));
    }
}
