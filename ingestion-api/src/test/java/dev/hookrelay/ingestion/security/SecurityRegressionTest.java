package dev.hookrelay.ingestion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.hookrelay.common.security.AesGcmSecretEncryptor;
import dev.hookrelay.common.security.ApiKeyGenerator;
import dev.hookrelay.common.security.GeneratedApiKey;
import dev.hookrelay.ingestion.apikey.ApiKeyManagementService;
import dev.hookrelay.ingestion.apikey.ApiKeyManagementService.RotatedApiKey;
import dev.hookrelay.ingestion.audit.AuditService;
import dev.hookrelay.ingestion.auth.ApiKeyAuthenticationFilter;
import dev.hookrelay.ingestion.endpoint.EndpointService;
import dev.hookrelay.ingestion.endpoint.EndpointService.CreatedEndpoint;
import dev.hookrelay.ingestion.provision.ProvisionedTenant;
import dev.hookrelay.ingestion.provision.TenantProvisioningService;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;

/**
 * HR-031 security regression suite (tenant isolation, SSRF, secret redaction).
 * GraphQL cross-tenant cases remain pending until HR-025.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityRegressionTest {

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
    private ApiKeyManagementService apiKeyManagementService;

    @Autowired
    private EndpointService endpointService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AesGcmSecretEncryptor secretEncryptor;

    @Test
    void revokedApiKeyIsRejectedAndRotationIssuesNewKeyWithoutPersistingRaw() throws Exception {
        ProvisionedTenant original = provisioningService.provision("Rotate Tenant");

        MvcResult rotateResult = mockMvc.perform(post("/v1/api-keys/rotate")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, original.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeCurrent\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.revokedApiKeyId").value(original.apiKeyId().toString()))
                .andReturn();

        JsonNode rotated = objectMapper.readTree(rotateResult.getResponse().getContentAsString());
        String newRawKey = rotated.get("apiKey").asText();
        assertThat(newRawKey).isNotEqualTo(original.rawApiKey());
        assertThat(rotated.toString()).doesNotContain(original.rawApiKey());

        Integer rawPersisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE key_hash = ? OR key_prefix = ?",
                Integer.class,
                newRawKey,
                newRawKey);
        assertThat(rawPersisted).isZero();

        mockMvc.perform(get("/v1/me")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, original.rawApiKey()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/me")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, newRawKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(original.tenantId().toString()));

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_events
                 WHERE tenant_id = ?::uuid
                   AND action IN (?, ?)
                """,
                Integer.class,
                original.tenantId(),
                AuditService.ACTION_API_KEY_ROTATED,
                AuditService.ACTION_API_KEY_REVOKED);
        assertThat(auditCount).isGreaterThanOrEqualTo(2);

        String auditBlob = jdbcTemplate.queryForObject(
                "SELECT COALESCE(string_agg(detail_json::text, ' '), '') FROM audit_events WHERE tenant_id = ?::uuid",
                String.class,
                original.tenantId());
        assertThat(auditBlob).doesNotContain(original.rawApiKey());
        assertThat(auditBlob).doesNotContain(newRawKey);
    }

    @Test
    void tenantCannotAccessAnotherTenantsEndpointEvenWithUuid() throws Exception {
        ProvisionedTenant tenantA = provisioningService.provision("Iso A");
        ProvisionedTenant tenantB = provisioningService.provision("Iso B");

        CreatedEndpoint created = endpointService.create(
                tenantA.tenantId(),
                "A Receiver",
                "https://example.com/hooks/a",
                List.of("payment.succeeded"));

        mockMvc.perform(get("/v1/endpoints/{id}", created.endpoint().id())
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantB.rawApiKey()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantB.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/v1/api-keys/{id}/revoke", tenantA.apiKeyId())
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantB.rawApiKey()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/hooks",
            "https://localhost/hooks",
            "https://10.0.0.8/hooks",
            "https://192.168.0.20/hooks",
            "https://172.16.5.5/hooks",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/hooks",
            "https://user:secret@example.com/hooks"
    })
    void rejectsSsrfAndUnsafeEndpointUrls(String url) throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("SSRF " + UUID.randomUUID());

        mockMvc.perform(post("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad",
                                  "url": "%s",
                                  "subscribedEventTypes": ["payment.succeeded"]
                                }
                                """.formatted(url)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signingSecretIsEncryptedAtRestAndRedactedFromSafeViews() {
        ProvisionedTenant tenant = provisioningService.provision("Secret Tenant");
        CreatedEndpoint created = endpointService.create(
                tenant.tenantId(),
                "Secure Receiver",
                "https://example.com/hooks/secure",
                List.of("order.created"));

        assertThat(created.toString()).doesNotContain(created.signingSecret());
        assertThat(created.signingSecret()).startsWith("whsec_");

        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT signing_secret_ciphertext FROM endpoints WHERE id = ?::uuid",
                byte[].class,
                created.endpoint().id());
        byte[] nonce = jdbcTemplate.queryForObject(
                "SELECT signing_secret_nonce FROM endpoints WHERE id = ?::uuid",
                byte[].class,
                created.endpoint().id());

        assertThat(new String(ciphertext)).isNotEqualTo(created.signingSecret());
        String decrypted = secretEncryptor.decrypt(
                new dev.hookrelay.common.security.EncryptedSecret(ciphertext, nonce, "test-v1"));
        assertThat(decrypted).isEqualTo(created.signingSecret());

        var listed = endpointService.list(tenant.tenantId());
        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().toString()).doesNotContain(created.signingSecret());
    }

    @Test
    void generatedSecretsRedactRawValuesInToString() {
        GeneratedApiKey generated = new ApiKeyGenerator().generate();
        assertThat(generated.toString()).doesNotContain(generated.rawKey());

        ProvisionedTenant provisioned = provisioningService.provision("Redact Tenant");
        assertThat(provisioned.toString()).doesNotContain(provisioned.rawApiKey());

        RotatedApiKey rotated = apiKeyManagementService.rotate(
                new dev.hookrelay.common.auth.TenantPrincipal(
                        provisioned.tenantId(),
                        provisioned.tenantName(),
                        provisioned.apiKeyId(),
                        provisioned.apiKeyPrefix()),
                false);
        assertThat(rotated.toString()).doesNotContain(rotated.rawApiKey());
    }
}
