package com.woodfurni.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for the Node.js gateway HTTP client.
 *
 * Two properties are used by the gateway client:
 *   - gateway.base-url  (default: http://localhost:3000)
 *   - gateway.internal-secret (required in production, has dev default)
 *
 * If the gateway is unreachable (dev mode, gateway down for maintenance),
 * the client fails gracefully — see NotificationClient — so the core business
 * flow (checkout, status update, inventory adjust) is never blocked by the
 * realtime channel.
 */
@Configuration
public class GatewayClientConfig {

    @Bean
    @ConfigurationProperties(prefix = "gateway")
    public GatewayProperties gatewayProperties() {
        return new GatewayProperties();
    }

    /**
     * RestTemplate with a short connect/read timeout so a slow/down gateway
     * cannot stall checkout or status update. The NotificationClient still
     * catches and logs errors, but a tight timeout keeps the worst-case
     * latency bounded.
     */
    @Bean("gatewayRestTemplate")
    public RestTemplate gatewayRestTemplate(
            @Value("${gateway.timeout.connect-ms:1500}") int connectMs,
            @Value("${gateway.timeout.read-ms:2000}") int readMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);

        return new RestTemplate(factory);
    }

    /**
     * Properties bound from application.yml:
     *
     * gateway:
     *   base-url: http://localhost:3000
     *   internal-secret: internal-woodfurni-shared-secret-change-me
     *   enabled: true
     */
    public static class GatewayProperties {
        private String baseUrl = "http://localhost:3000";
        private String internalSecret;
        private boolean enabled = true;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getInternalSecret() { return internalSecret; }
        public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}