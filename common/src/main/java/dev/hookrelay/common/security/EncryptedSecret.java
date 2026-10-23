package dev.hookrelay.common.security;

import java.util.Arrays;
import java.util.Objects;

/**
 * AES-GCM ciphertext plus the nonce/IV used for encryption.
 */
public record EncryptedSecret(byte[] ciphertext, byte[] nonce, String keyId) {

    public EncryptedSecret {
        Objects.requireNonNull(ciphertext, "ciphertext");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(keyId, "keyId");
        if (ciphertext.length == 0) {
            throw new IllegalArgumentException("ciphertext must not be empty");
        }
        if (nonce.length == 0) {
            throw new IllegalArgumentException("nonce must not be empty");
        }
        ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
        nonce = Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public byte[] ciphertext() {
        return Arrays.copyOf(ciphertext, ciphertext.length);
    }

    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public String toString() {
        return "EncryptedSecret[keyId=" + keyId + ", ciphertext=REDACTED, nonce=REDACTED]";
    }
}
