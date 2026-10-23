package dev.hookrelay.ingestion.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
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

import dev.hookrelay.ingestion.auth.ApiKeyAuthenticationFilter;
import dev.hookrelay.ingestion.provision.ProvisionedTenant;
import dev.hookrelay.ingestion.provision.TenantProvisioningService;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EndpointCrudIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListGetUpdateAndDeactivateAreTenantScopedAndHideStoredSecret() throws Exception {
        ProvisionedTenant tenantA = provisioningService.provision("Tenant A");
        ProvisionedTenant tenantB = provisioningService.provision("Tenant B");

        MvcResult createResult = mockMvc.perform(post("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Order service",
                                  "url": "https://receiver.example.com/hooks/hookrelay",
                                  "subscribedEventTypes": ["payment.succeeded", "order.cancelled"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.signingSecret").isNotEmpty())
                .andExpect(jsonPath("$.subscribedEventTypes", containsInAnyOrder(
                        "payment.succeeded", "order.cancelled")))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String endpointId = created.get("id").asText();
        String signingSecret = created.get("signingSecret").asText();
        assertThat(signingSecret).startsWith("whsec_");

        byte[] ciphertext = jdbcTemplate.queryForObject(
                "SELECT signing_secret_ciphertext FROM endpoints WHERE id = ?::uuid",
                byte[].class,
                endpointId);
        assertThat(ciphertext).isNotNull();
        assertThat(new String(ciphertext, StandardCharsets.UTF_8)).isNotEqualTo(signingSecret);

        mockMvc.perform(get("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(endpointId))
                .andExpect(jsonPath("$.items[0].signingSecret").doesNotExist());

        mockMvc.perform(get("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Order service"))
                .andExpect(jsonPath("$.signingSecret").doesNotExist());

        mockMvc.perform(get("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantB.rawApiKey()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Orders v2",
                                  "status": "PAUSED",
                                  "subscribedEventTypes": ["payment.succeeded"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Orders v2"))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.subscribedEventTypes", containsInAnyOrder("payment.succeeded")))
                .andExpect(jsonPath("$.signingSecret").doesNotExist());

        mockMvc.perform(patch("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantB.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijack"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/endpoints/{id}", endpointId)
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenantA.rawApiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM endpoints WHERE id = ?::uuid",
                String.class,
                endpointId);
        assertThat(status).isEqualTo("DISABLED");
    }

    @Test
    void rejectsInvalidCreatePayload() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Validation Tenant");

        mockMvc.perform(post("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad",
                                  "url": "ftp://example.com/hook",
                                  "subscribedEventTypes": ["payment.succeeded"]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/endpoints")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad",
                                  "url": "https://user:pass@example.com/hook",
                                  "subscribedEventTypes": ["payment.succeeded"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
