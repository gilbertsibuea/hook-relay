package dev.hookrelay.common.security;

/**
 * Newly generated API key material. The {@code rawKey} must be shown once to the operator
 * and must never be persisted or logged.
 */
public record GeneratedApiKey(String rawKey, String keyPrefix) {

    public GeneratedApiKey {
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalArgumentException("rawKey must not be blank");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
    }

    @Override
    public String toString() {
        return "GeneratedApiKey[keyPrefix=" + keyPrefix + ", rawKey=REDACTED]";
    }
}
