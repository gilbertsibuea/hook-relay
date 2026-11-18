package dev.hookrelay.ingestion.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
