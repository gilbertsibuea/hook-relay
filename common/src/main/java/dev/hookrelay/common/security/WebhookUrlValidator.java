package dev.hookrelay.common.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Validates webhook endpoint URLs against SSRF baseline rules.
 *
 * <p>Checks are applied at configuration time (create/update) and should be re-run
 * before outbound delivery. DNS rebinding remains a residual risk; pair with egress controls.
 */
public final class WebhookUrlValidator {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata",
            "kubernetes.default",
            "kubernetes.default.svc");

    private final HostResolver hostResolver;

    public WebhookUrlValidator() {
        this(InetAddress::getAllByName);
    }

    public WebhookUrlValidator(HostResolver hostResolver) {
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
    }

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * @param allowHttp when false, only {@code https} is accepted (production default)
     * @return normalized trimmed URL string
     */
    public String validate(String rawUrl, boolean allowHttp) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new WebhookUrlValidationException("url must not be blank");
        }

        String trimmed = rawUrl.trim();
        final URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new WebhookUrlValidationException("url must be a valid URI");
        }

        if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null) {
            throw new WebhookUrlValidationException("url must be an absolute http(s) URI");
        }
        if (uri.getUserInfo() != null) {
            throw new WebhookUrlValidationException("url must not contain userinfo");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            // always allowed
        } else if ("http".equals(scheme)) {
            if (!allowHttp) {
                throw new WebhookUrlValidationException("url scheme must be https in this environment");
            }
        } else {
            throw new WebhookUrlValidationException("url scheme must be http or https");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        // URI may wrap IPv6 in brackets; getHost() already strips them.
        if (BLOCKED_HOSTNAMES.contains(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new WebhookUrlValidationException("url host is not allowed: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new WebhookUrlValidationException("url host could not be resolved: " + host);
        }
        if (addresses == null || addresses.length == 0) {
            throw new WebhookUrlValidationException("url host could not be resolved: " + host);
        }

        for (InetAddress address : addresses) {
            rejectIfForbidden(address);
        }

        return trimmed;
    }

    private static void rejectIfForbidden(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            throw new WebhookUrlValidationException(
                    "url resolves to a forbidden address: " + address.getHostAddress());
        }

        byte[] octets = address.getAddress();
        if (octets.length == 4) {
            // 100.64.0.0/10 carrier-grade NAT
            int first = octets[0] & 0xFF;
            int second = octets[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) {
                throw new WebhookUrlValidationException(
                        "url resolves to a forbidden address: " + address.getHostAddress());
            }
            // Explicit metadata / documentation ranges already covered by link-local for 169.254/16
        }

        if (octets.length == 16) {
            // Unique local addresses fc00::/7
            int first = octets[0] & 0xFF;
            if ((first & 0xFE) == 0xFC) {
                throw new WebhookUrlValidationException(
                        "url resolves to a forbidden address: " + address.getHostAddress());
            }
        }
    }
}
