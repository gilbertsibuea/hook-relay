package dev.hookrelay.ingestion.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    @Query("""
            select o from OutboxEntity o
             where o.publishedAt is null
             order by o.createdAt asc, o.id asc
            """)
    List<OutboxEntity> findUnpublished(Pageable pageable);

    long countByPublishedAtIsNull();
}
