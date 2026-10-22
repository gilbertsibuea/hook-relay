package dev.hookrelay.ingestion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.hookrelay.ingestion.apikey.ApiKeyEntity;
import dev.hookrelay.ingestion.apikey.ApiKeyRepository;
import dev.hookrelay.ingestion.apikey.ApiKeyStatus;
import dev.hookrelay.ingestion.provision.ProvisionedTenant;
import dev.hookrelay.ingestion.provision.TenantProvisioningService;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ApiKeyAuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantProvisioningService provisioningService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void validApiKeyResolvesTenantAndNeverPersistsRawKey() throws Exception {
        ProvisionedTenant provisioned = provisioningService.provision("Auth Tenant");

        Integer rawKeyMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE key_hash = ? OR key_prefix = ?",
                Integer.class,
                provisioned.rawApiKey(),
                provisioned.rawApiKey());
        assertThat(rawKeyMatches).isZero();

        Integer hashMatches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE id = ? AND key_hash <> ?",
                Integer.class,
                provisioned.apiKeyId(),
                provisioned.rawApiKey());
        assertThat(hashMatches).isOne();

        mockMvc.perform(get("/v1/me").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, provisioned.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(provisioned.tenantId().toString()))
                .andExpect(jsonPath("$.tenantName").value("Auth Tenant"))
                .andExpect(jsonPath("$.apiKeyPrefix").value(provisioned.apiKeyPrefix()));
    }

    @Test
    void missingOrInvalidOrRevokedKeyIsRejected() throws Exception {
        mockMvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/me").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, "hr_live_not-a-real-key"))
                .andExpect(status().isUnauthorized());

        ProvisionedTenant provisioned = provisioningService.provision("Revoke Tenant");
        ApiKeyEntity apiKey = apiKeyRepository.findById(provisioned.apiKeyId()).orElseThrow();
        apiKey.revoke(apiKey.getCreatedAt());
        apiKeyRepository.save(apiKey);

        mockMvc.perform(get("/v1/me").header(ApiKeyAuthenticationFilter.API_KEY_HEADER, provisioned.rawApiKey()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorHealthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
