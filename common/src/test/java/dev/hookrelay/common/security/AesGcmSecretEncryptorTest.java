package dev.hookrelay.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class AesGcmSecretEncryptorTest {

    @Test
    void roundTripsPlaintextAndHidesSecretInToString() {
        AesGcmSecretEncryptor encryptor = AesGcmSecretEncryptor.fromBase64Key(
                AesGcmSecretEncryptor.fixedTestKeyBase64(),
                "test-v1");

        String secret = AesGcmSecretEncryptor.generateSigningSecret(new SecureRandom());
        EncryptedSecret encrypted = encryptor.encrypt(secret);

        assertEquals(secret, encryptor.decrypt(encrypted));
        assertNotEquals(secret, new String(encrypted.ciphertext()));
        assertFalse(encrypted.toString().contains(secret));
        assertTrue(secret.startsWith("whsec_"));
    }

    @Test
    void rejectsPlaceholderKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AesGcmSecretEncryptor.fromBase64Key("REPLACE_WITH_LOCAL_BASE64_KEY", "local-v1"));
    }
}
