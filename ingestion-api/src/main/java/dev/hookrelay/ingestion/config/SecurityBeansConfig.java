package dev.hookrelay.ingestion.config;

import java.security.SecureRandom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.hookrelay.common.security.AesGcmSecretEncryptor;
import dev.hookrelay.common.security.ApiKeyGenerator;
import dev.hookrelay.common.security.ApiKeyHasher;

@Configuration
@EnableConfigurationProperties({ApiKeyProperties.class, EndpointSecretProperties.class})
public class SecurityBeansConfig {

    @Bean
    ApiKeyGenerator apiKeyGenerator() {
        return new ApiKeyGenerator();
    }

    @Bean
    ApiKeyHasher apiKeyHasher(ApiKeyProperties properties) {
        return new ApiKeyHasher(properties.getPepper());
    }

    @Bean
    AesGcmSecretEncryptor aesGcmSecretEncryptor(EndpointSecretProperties properties) {
        return AesGcmSecretEncryptor.fromBase64Key(properties.getKeyBase64(), properties.getKeyId());
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
