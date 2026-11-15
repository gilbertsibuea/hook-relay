package dev.hookrelay.common.security;

/**
 * Raised when a webhook URL fails SSRF / scheme / host validation.
 */
public class WebhookUrlValidationException extends RuntimeException {

    private final String reason;

    public WebhookUrlValidationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
