package dev.hookrelay.ingestion.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import dev.hookrelay.ingestion.outbox.OutboxEntity;
import dev.hookrelay.ingestion.provision.ProvisionedTenant;
import dev.hookrelay.ingestion.provision.TenantProvisioningService;
import dev.hookrelay.ingestion.support.PostgresTestcontainers;
import dev.hookrelay.ingestion.web.EventController;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "hookrelay.ingestion.max-payload-bytes=2048")
@AutoConfigureMockMvc
class EventIngestionIntegrationTest {

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
    void acceptsValidEventWritesEventIdempotencyAndOutbox() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Ingest Tenant");

        MvcResult result = mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "order-1-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "payment.succeeded",
                                  "payload": {"orderId":"order-1","amount":1000}
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String eventId = body.get("eventId").asText();

        Integer events = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE id = ?::uuid AND tenant_id = ?::uuid AND status = 'ACCEPTED'",
                Integer.class,
                eventId,
                tenant.tenantId());
        assertThat(events).isOne();

        Integer keys = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM idempotency_keys
                 WHERE tenant_id = ?::uuid AND idempotency_key = 'order-1-v1' AND event_id = ?::uuid
                """,
                Integer.class,
                tenant.tenantId(),
                eventId);
        assertThat(keys).isOne();

        Integer outbox = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM outbox
                 WHERE aggregate_id = ?::uuid
                   AND message_type = ?
                   AND published_at IS NULL
                """,
                Integer.class,
                eventId,
                OutboxEntity.MESSAGE_EVENT_ACCEPTED);
        assertThat(outbox).isOne();
    }

    @Test
    void sameKeyAndPayloadReturnsSameEventIdWithoutDuplicatingRows() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Idempotent Tenant");
        String request = """
                {
                  "type": "payment.succeeded",
                  "payload": {"orderId":"order-2"}
                }
                """;

        MvcResult first = mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "same-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andReturn();

        String eventId1 = objectMapper.readTree(first.getResponse().getContentAsString()).get("eventId").asText();
        String eventId2 = objectMapper.readTree(second.getResponse().getContentAsString()).get("eventId").asText();
        assertThat(eventId1).isEqualTo(eventId2);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE tenant_id = ?::uuid",
                Integer.class,
                tenant.tenantId());
        assertThat(eventCount).isOne();
    }

    @Test
    void sameKeyDifferentPayloadReturns409() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Conflict Tenant");

        mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.succeeded","payload":{"orderId":"a"}}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.succeeded","payload":{"orderId":"b"}}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingIdempotencyKeyMalformedJsonAndOversizedPayload() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Validation Tenant");

        mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.succeeded","payload":{"orderId":"x"}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "bad-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());

        String bigPayload = "\"" + "x".repeat(3000) + "\"";
        mockMvc.perform(post("/v1/events")
                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "too-big")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.succeeded","payload":{"blob":%s}}
                                """.formatted(bigPayload)))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void concurrentSameKeyCreatesOnlyOneEvent() throws Exception {
        ProvisionedTenant tenant = provisioningService.provision("Race Tenant");
        String body = """
                {"type":"payment.succeeded","payload":{"orderId":"race-1"}}
                """;

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return mockMvc.perform(post("/v1/events")
                                        .header(ApiKeyAuthenticationFilter.API_KEY_HEADER, tenant.rawApiKey())
                                        .header(EventController.IDEMPOTENCY_KEY_HEADER, "race-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                                .andReturn();
                    }))
                    .toList();

            start.countDown();
            var eventIds = futures.stream()
                    .map(EventIngestionIntegrationTest::join)
                    .map(result -> {
                        assertThat(result.getResponse().getStatus()).isEqualTo(202);
                        try {
                            return objectMapper.readTree(result.getResponse().getContentAsString())
                                    .get("eventId")
                                    .asText();
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .distinct()
                    .toList();

            assertThat(eventIds).hasSize(1);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM events WHERE tenant_id = ?::uuid",
                    Integer.class,
                    tenant.tenantId());
            assertThat(count).isOne();
        } finally {
            pool.shutdownNow();
        }
    }

    private static MvcResult join(Future<MvcResult> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
