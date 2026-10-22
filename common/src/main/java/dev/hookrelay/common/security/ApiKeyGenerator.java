package dev.hookrelay.common.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates high-entropy HookRelay API keys.
 *
 * <p>Format: {@code hr_live_<url-safe-base64>} where the secret portion is 32 random bytes
 * (256 bits). The stored {@code key_prefix} is a non-secret identifier derived from the raw key.
 */
public final class ApiKeyGenerator {

    public static final String KEY_PREFIX_MARKER = "hr_live_";
    public static final int PREFIX_LENGTH = 16;
    private static final int SECRET_BYTES = 32;

    private final SecureRandom secureRandom;

    public ApiKeyGenerator() {
        this(new SecureRandom());
    }

    public ApiKeyGenerator(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public GeneratedApiKey generate() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String rawKey = KEY_PREFIX_MARKER + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        return new GeneratedApiKey(rawKey, prefix);
    }
}
