package dev.hookrelay.ingestion.event;

public class PayloadTooLargeException extends RuntimeException {

    private final int maxBytes;

    public PayloadTooLargeException(int actualBytes, int maxBytes) {
        super("payload exceeds maximum size of " + maxBytes + " bytes (got " + actualBytes + ")");
        this.maxBytes = maxBytes;
    }

    public int getMaxBytes() {
        return maxBytes;
    }
}
