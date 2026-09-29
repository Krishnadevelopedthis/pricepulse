package com.pricepulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pricepulse")
public record PricePulseProperties(
        String corsAllowedOriginPatterns,
        int maxProductsPerUser,
        Scheduler scheduler,
        Fetch fetch) {

    public record Scheduler(boolean enabled, long tickMs, int checkIntervalMinutes, int maxBackoffExponent,
                            int batchSize, int poolSize, int queueCapacity) {}

    public record Fetch(int connectTimeoutMs, int requestTimeoutMs, int maxAttempts, int maxBodyBytes,
                        int maxRedirects, boolean allowPrivateHosts) {}
}
