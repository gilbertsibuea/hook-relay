package dev.hookrelay.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IngestionApiApplicationTest {

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

    @Test
    void contextLoads() {
        // Verifies application wiring + Flyway against PostgreSQL.
    }
}
