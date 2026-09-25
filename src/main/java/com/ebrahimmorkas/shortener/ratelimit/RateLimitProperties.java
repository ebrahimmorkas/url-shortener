package com.ebrahimmorkas.shortener.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Policy> policies) {

    public record Policy(int limit, Duration window) {
    }
}
