package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookrelay.ingestion")
public class IngestionProperties {

    /**
     * Maximum accepted JSON payload size in bytes (default 256 KiB).
     */
    private int maxPayloadBytes = 256 * 1024;

    /**
     * Default retention hint for accepted events (days). Stored as expires_at.
     */
    private int eventRetentionDays = 30;

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getEventRetentionDays() {
        return eventRetentionDays;
    }

    public void setEventRetentionDays(int eventRetentionDays) {
        this.eventRetentionDays = eventRetentionDays;
    }
}
