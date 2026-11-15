package dev.hookrelay.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookrelay.ssrf")
public class SsrfProperties {

    /**
     * When false, only https webhook URLs are accepted (production).
     * Development/test may allow http for local receivers.
     */
    private boolean allowHttp = true;

    public boolean isAllowHttp() {
        return allowHttp;
    }

    public void setAllowHttp(boolean allowHttp) {
        this.allowHttp = allowHttp;
    }
}
