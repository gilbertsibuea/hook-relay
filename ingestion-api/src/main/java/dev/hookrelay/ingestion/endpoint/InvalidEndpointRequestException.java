package dev.hookrelay.ingestion.endpoint;

public class InvalidEndpointRequestException extends RuntimeException {

    public InvalidEndpointRequestException(String message) {
        super(message);
    }
}
