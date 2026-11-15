package dev.hookrelay.common.security;

/**
 * Policy for webhook URL validation.
 *
 * @param allowHttp when {@code false}, only {@code https} is accepted (production default)
 * @param resolveDns when {@code true}, hostnames are resolved and resulting IPs are checked
 */
public record WebhookUrlPolicy(boolean allowHttp, boolean resolveDns) {

    public static WebhookUrlPolicy production() {
        return new WebhookUrlPolicy(false, true);
    }

    public static WebhookUrlPolicy development() {
        return new WebhookUrlPolicy(true, true);
    }
}
