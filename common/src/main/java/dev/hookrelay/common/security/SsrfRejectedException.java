package dev.hookrelay.common.security;

/**
 * Raised when a webhook URL fails SSRF / URL-safety checks.
 */
public class SsrfRejectedException extends RuntimeException {

    private final String reason;

    public SsrfRejectedException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
