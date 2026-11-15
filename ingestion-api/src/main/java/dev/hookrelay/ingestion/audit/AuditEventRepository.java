package dev.hookrelay.ingestion.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    List<AuditEventEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AuditEventEntity> findByTenantIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
            UUID tenantId,
            String resourceType,
            UUID resourceId);
}
