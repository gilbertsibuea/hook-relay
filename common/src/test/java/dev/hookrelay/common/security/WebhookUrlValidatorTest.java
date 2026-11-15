package dev.hookrelay.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator(host -> new InetAddress[] {
        InetAddress.getByName(host)
    });

    @Test
    void acceptsPublicHttps() {
        WebhookUrlValidator fixed = new WebhookUrlValidator(host -> new InetAddress[] {
            InetAddress.getByAddress(host, new byte[] {(byte) 93, (byte) 184, (byte) 216, (byte) 34})
        });
        String url = fixed.validate("https://example.com/hooks", false);
        assertEquals("https://example.com/hooks", url);
    }

    @Test
    void rejectsHttpWhenNotAllowed() {
        WebhookUrlValidationException ex = assertThrows(
                WebhookUrlValidationException.class,
                () -> validator.validate("http://example.com/hooks", false));
        assertTrue(ex.getReason().contains("https"));
    }

    @Test
    void allowsHttpWhenPolicyPermits() throws Exception {
        WebhookUrlValidator fixed = new WebhookUrlValidator(host -> new InetAddress[] {
            InetAddress.getByAddress(host, new byte[] {(byte) 93, (byte) 184, (byte) 216, (byte) 34})
        });
        assertDoesNotThrow(() -> fixed.validate("http://example.com/hooks", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/hooks",
            "https://localhost/hooks",
            "https://[::1]/hooks",
            "https://10.0.0.5/hooks",
            "https://192.168.1.10/hooks",
            "https://172.16.0.1/hooks",
            "https://169.254.169.254/latest/meta-data",
            "https://0.0.0.0/hooks",
            "https://user:pass@example.com/hooks",
            "ftp://example.com/hooks",
            "https://metadata.google.internal/computeMetadata/v1/"
    })
    void rejectsForbiddenUrls(String url) {
        assertThrows(WebhookUrlValidationException.class, () -> validator.validate(url, true));
    }

    @Test
    void rejectsHostnameThatResolvesToPrivateIp() {
        WebhookUrlValidator resolver = new WebhookUrlValidator(host -> {
            if ("evil.example".equals(host)) {
                return new InetAddress[] {
                        InetAddress.getByAddress(host, new byte[] {(byte) 10, 1, 2, 3})
                };
            }
            throw new UnknownHostException(host);
        });
        assertThrows(
                WebhookUrlValidationException.class,
                () -> resolver.validate("https://evil.example/hooks", false));
    }

    @Test
    void rejectsUnresolvedHost() {
        WebhookUrlValidator resolver = new WebhookUrlValidator(host -> {
            throw new UnknownHostException(host);
        });
        assertThrows(
                WebhookUrlValidationException.class,
                () -> resolver.validate("https://no-such-host.invalid/hooks", false));
    }
}
