package dev.hookrelay.ingestion.provision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.hookrelay.common.security.ApiKeyHasher;
import dev.hookrelay.ingestion.apikey.ApiKeyStatus;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;
import dev.hookrelay.ingestion.tenant.TenantStatus;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantProvisioningServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
    }

    @Autowired
    private TenantProvisioningService provisioningService;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void provisionsActiveTenantAndHashedApiKey() {
        ProvisionedTenant provisioned = provisioningService.provision("  Dev Tenant  ");

        assertThat(provisioned.tenantName()).isEqualTo("Dev Tenant");
        assertThat(provisioned.rawApiKey()).startsWith("hr_live_");

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM tenants WHERE id = ?",
                String.class,
                provisioned.tenantId());
        assertThat(status).isEqualTo(TenantStatus.ACTIVE.name());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT key_hash FROM api_keys WHERE id = ?",
                String.class,
                provisioned.apiKeyId());
        assertThat(storedHash).isEqualTo(apiKeyHasher.hash(provisioned.rawApiKey()));
        assertThat(storedHash).isNotEqualTo(provisioned.rawApiKey());

        String keyStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM api_keys WHERE id = ?",
                String.class,
                provisioned.apiKeyId());
        assertThat(keyStatus).isEqualTo(ApiKeyStatus.ACTIVE.name());

        Integer rawPersisted = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM api_keys
                 WHERE key_hash = ? OR CAST(id AS text) = ?
                """,
                Integer.class,
                provisioned.rawApiKey(),
                provisioned.rawApiKey());
        assertThat(rawPersisted).isZero();
        assertThat(provisioned.toString()).doesNotContain(provisioned.rawApiKey());
    }

    @Test
    void rejectsBlankTenantName() {
        assertThatThrownBy(() -> provisioningService.provision("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provisionCommandArgParsing() {
        assertThat(ProvisionTenantCommand.COMMAND).isEqualTo("provision-tenant");
    }
}
