package dev.hookrelay.ingestion.event;

public class InvalidEventRequestException extends RuntimeException {

    public InvalidEventRequestException(String message) {
        super(message);
    }
}
