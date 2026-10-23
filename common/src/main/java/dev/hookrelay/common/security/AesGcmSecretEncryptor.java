package dev.hookrelay.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts endpoint signing secrets with AES-256-GCM.
 */
public final class AesGcmSecretEncryptor {

    public static final String DEFAULT_KEY_ID = "local-v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final String keyId;
    private final SecureRandom secureRandom;

    public AesGcmSecretEncryptor(byte[] rawKey, String keyId) {
        this(rawKey, keyId, new SecureRandom());
    }

    public AesGcmSecretEncryptor(byte[] rawKey, String keyId, SecureRandom secureRandom) {
        Objects.requireNonNull(rawKey, "rawKey");
        if (rawKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be exactly 32 bytes");
        }
        this.secretKey = new SecretKeySpec(Arrays.copyOf(rawKey, rawKey.length), "AES");
        this.keyId = Objects.requireNonNullElse(keyId, DEFAULT_KEY_ID);
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public static AesGcmSecretEncryptor fromBase64Key(String base64Key, String keyId) {
        if (base64Key == null || base64Key.isBlank()
                || base64Key.startsWith("REPLACE_WITH")) {
            throw new IllegalArgumentException(
                    "hookrelay.endpoint-secret.key-base64 must be a 32-byte key encoded as base64");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("endpoint secret key is not valid base64", ex);
        }
        return new AesGcmSecretEncryptor(decoded, keyId);
    }

    public EncryptedSecret encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(ciphertext, nonce, keyId);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decrypt(EncryptedSecret encrypted) {
        Objects.requireNonNull(encrypted, "encrypted");
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(GCM_TAG_BITS, encrypted.nonce()));
            byte[] plaintext = cipher.doFinal(encrypted.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    /**
     * Generates a cryptographically strong signing secret for webhook HMAC.
     */
    public static String generateSigningSecret(SecureRandom secureRandom) {
        byte[] secret = new byte[KEY_BYTES];
        secureRandom.nextBytes(secret);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public static String generateBase64Key(SecureRandom secureRandom) {
        byte[] key = new byte[KEY_BYTES];
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /** Deterministic 32-byte key for tests only. */
    public static String fixedTestKeyBase64() {
        ByteBuffer buffer = ByteBuffer.allocate(KEY_BYTES);
        for (int i = 0; i < KEY_BYTES; i++) {
            buffer.put((byte) (i + 1));
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }
}
