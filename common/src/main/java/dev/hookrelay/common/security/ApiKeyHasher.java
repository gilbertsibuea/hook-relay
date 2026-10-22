package dev.hookrelay.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Hashes raw API keys for storage and authentication lookup.
 *
 * <p>Uses SHA-256 so authentication can resolve a key with a single indexed lookup on
 * {@code key_hash}. An optional server-side pepper hardens against offline hash cracking if
 * the database is leaked.
 */
public final class ApiKeyHasher {

    private final String pepper;

    public ApiKeyHasher(String pepper) {
        this.pepper = Objects.requireNonNullElse(pepper, "");
    }

    public String hash(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new IllegalArgumentException("rawApiKey must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            digest.update(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
