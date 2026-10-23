package dev.hookrelay.ingestion.endpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EndpointRepository extends JpaRepository<EndpointEntity, UUID> {

    List<EndpointEntity> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    Optional<EndpointEntity> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
