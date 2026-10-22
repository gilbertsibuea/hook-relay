package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookrelay.api-key")
public class ApiKeyProperties {

    /**
     * Optional server-side pepper mixed into API key hashes.
     * Keep this in environment/secrets; changing it invalidates existing keys.
     */
    private String pepper = "";

    public String getPepper() {
        return pepper;
    }

    public void setPepper(String pepper) {
        this.pepper = pepper == null ? "" : pepper;
    }
}
