package dev.hookrelay.ingestion.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Computes a stable request hash for idempotency comparison.
 *
 * <p>Hash input is {@code eventType + '\\n' + compact JSON payload}. Whitespace differences in the
 * original HTTP body are normalized by Jackson parsing; object key order is preserved as parsed.
 */
@Component
public class RequestHashCalculator {

    private final ObjectMapper objectMapper;

    public RequestHashCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(String eventType, JsonNode payload) {
        try {
            String canonical = eventType + "\n" + objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("payload could not be serialized for hashing", ex);
        }
    }
}
