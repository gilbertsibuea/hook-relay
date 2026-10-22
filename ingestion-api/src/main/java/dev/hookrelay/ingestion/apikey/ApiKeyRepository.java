package dev.hookrelay.ingestion.apikey;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    Optional<ApiKeyEntity> findByKeyHash(String keyHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ApiKeyEntity k
               set k.lastUsedAt = CURRENT_TIMESTAMP
             where k.id = :id
            """)
    int touchLastUsedAt(@Param("id") UUID id);
}
