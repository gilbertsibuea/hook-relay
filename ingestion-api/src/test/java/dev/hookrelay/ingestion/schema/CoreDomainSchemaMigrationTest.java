package dev.hookrelay.ingestion.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreDomainSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.15-alpine"))
            .withDatabaseName("hookrelay")
            .withUsername("hookrelay")
            .withPassword("hookrelay");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude",
                () -> String.join(",",
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"));
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesRequiredTablesAndUniqueConstraintsFromEmptyDatabase() throws SQLException {
        Set<String> tables = loadPublicTables();
        assertTrue(tables.containsAll(Set.of(
                "tenants",
                "api_keys",
                "endpoints",
                "endpoint_subscriptions",
                "events",
                "idempotency_keys",
                "outbox")));

        Set<String> indexes = loadIndexNames();
        assertTrue(indexes.contains("uq_api_keys_key_hash"));
        assertTrue(indexes.contains("uq_idempotency_keys_tenant_key"));
        assertTrue(indexes.contains("uq_endpoint_subscriptions_endpoint_event"));
        assertTrue(indexes.contains("idx_outbox_unpublished_created_at"));
        assertTrue(indexes.contains("idx_events_tenant_created_at"));
        assertTrue(indexes.contains("idx_endpoints_tenant_status"));
    }

    @Test
    void enforcesUniqueApiKeyHashAndIdempotencyKeyPerTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO tenants (id, name, status)
                VALUES (?, 'Acme', 'ACTIVE')
                """, tenantId);

        jdbcTemplate.update("""
                INSERT INTO api_keys (id, tenant_id, key_prefix, key_hash, status)
                VALUES (?, ?, 'hr_live_abcd', 'hash-1', 'ACTIVE')
                """, UUID.randomUUID(), tenantId);

        DataIntegrityViolationException duplicateHash = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO api_keys (id, tenant_id, key_prefix, key_hash, status)
                        VALUES (?, ?, 'hr_live_efgh', 'hash-1', 'ACTIVE')
                        """, UUID.randomUUID(), tenantId));
        assertTrue(duplicateHash.getMessage().toLowerCase().contains("uq_api_keys_key_hash")
                || containsConstraintName(duplicateHash, "uq_api_keys_key_hash"));

        jdbcTemplate.update("""
                INSERT INTO events (id, tenant_id, event_type, payload_json, status)
                VALUES (?, ?, 'payment.succeeded', '{"orderId":"1"}'::jsonb, 'ACCEPTED')
                """, eventId, tenantId);

        jdbcTemplate.update("""
                INSERT INTO idempotency_keys
                    (id, tenant_id, idempotency_key, request_hash, event_id)
                VALUES (?, ?, 'order-1', 'req-hash-a', ?)
                """, UUID.randomUUID(), tenantId, eventId);

        DataIntegrityViolationException duplicateIdempotency = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO idempotency_keys
                            (id, tenant_id, idempotency_key, request_hash, event_id)
                        VALUES (?, ?, 'order-1', 'req-hash-b', ?)
                        """, UUID.randomUUID(), tenantId, eventId));
        assertTrue(duplicateIdempotency.getMessage().toLowerCase().contains("uq_idempotency_keys_tenant_key")
                || containsConstraintName(duplicateIdempotency, "uq_idempotency_keys_tenant_key"));
    }

    @Test
    void flywayVersionIsApplied() {
        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class);
        assertEquals(1, versionCount);
    }

    private Set<String> loadPublicTables() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        return tables;
    }

    private Set<String> loadIndexNames() throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT indexname
                     FROM pg_indexes
                     WHERE schemaname = 'public'
                     """)) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString(1));
            }
        }
        return indexes;
    }

    private static boolean containsConstraintName(Throwable error, String constraintName) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
