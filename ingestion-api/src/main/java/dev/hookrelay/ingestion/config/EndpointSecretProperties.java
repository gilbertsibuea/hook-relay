package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import dev.hookrelay.common.security.AesGcmSecretEncryptor;

@ConfigurationProperties(prefix = "hookrelay.endpoint-secret")
public class EndpointSecretProperties {

    /**
     * Base64-encoded 32-byte AES key used to encrypt endpoint signing secrets at rest.
     */
    private String keyBase64 = "";

    private String keyId = AesGcmSecretEncryptor.DEFAULT_KEY_ID;

    public String getKeyBase64() {
        return keyBase64;
    }

    public void setKeyBase64(String keyBase64) {
        this.keyBase64 = keyBase64 == null ? "" : keyBase64;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId == null || keyId.isBlank()
                ? AesGcmSecretEncryptor.DEFAULT_KEY_ID
                : keyId;
    }
}
