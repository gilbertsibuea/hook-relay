package dev.hookrelay.ingestion.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers PostgreSQL wiring for ingestion-api tests.
 */
public final class PostgresTestcontainers {

    public static final DockerImageName IMAGE = DockerImageName.parse("postgres:16.15-alpine");

    private PostgresTestcontainers() {
    }

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("hookrelay")
                .withUsername("hookrelay")
                .withPassword("hookrelay");
    }

    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("hookrelay.api-key.pepper", () -> "test-pepper");
        registry.add("spring.autoconfigure.exclude",
                () -> String.join(",",
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                        "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
                        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"));
    }
}
