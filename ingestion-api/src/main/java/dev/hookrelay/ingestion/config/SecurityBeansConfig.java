package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.hookrelay.common.security.ApiKeyGenerator;
import dev.hookrelay.common.security.ApiKeyHasher;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class SecurityBeansConfig {

    @Bean
    ApiKeyGenerator apiKeyGenerator() {
        return new ApiKeyGenerator();
    }

    @Bean
    ApiKeyHasher apiKeyHasher(ApiKeyProperties properties) {
        return new ApiKeyHasher(properties.getPepper());
    }
}
