package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookrelay.outbox")
public class OutboxProperties {

    private boolean relayEnabled = true;
    private int batchSize = 50;
    private long pollIntervalMs = 1000;
    private String eventsAcceptedTopic = "hookrelay.events.accepted";

    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getEventsAcceptedTopic() {
        return eventsAcceptedTopic;
    }

    public void setEventsAcceptedTopic(String eventsAcceptedTopic) {
        this.eventsAcceptedTopic = eventsAcceptedTopic;
    }
}
