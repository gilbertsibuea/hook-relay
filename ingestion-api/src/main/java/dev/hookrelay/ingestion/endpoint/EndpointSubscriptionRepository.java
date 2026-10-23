package dev.hookrelay.ingestion.endpoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndpointSubscriptionRepository extends JpaRepository<EndpointSubscriptionEntity, UUID> {

    List<EndpointSubscriptionEntity> findByEndpointIdInOrderByEventTypeAsc(Collection<UUID> endpointIds);

    List<EndpointSubscriptionEntity> findByEndpointIdOrderByEventTypeAsc(UUID endpointId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EndpointSubscriptionEntity s where s.endpointId = :endpointId")
    void deleteByEndpointId(@Param("endpointId") UUID endpointId);
}
