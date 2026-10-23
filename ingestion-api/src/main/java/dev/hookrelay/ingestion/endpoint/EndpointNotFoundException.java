package dev.hookrelay.ingestion.endpoint;

import java.util.UUID;

public class EndpointNotFoundException extends RuntimeException {

    private final UUID endpointId;

    public EndpointNotFoundException(UUID endpointId) {
        super("Endpoint not found: " + endpointId);
        this.endpointId = endpointId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }
}
