package com.ebrahimmorkas.shortener.ratelimit;

import com.ebrahimmorkas.shortener.ratelimit.RateLimitProperties.Policy;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Distributed sliding-window rate limiter backed by a Redis Lua script, so every application
 * instance shares the same counters and the check-and-increment is atomic.
 */
@Slf4j
@Component
public class RedisRateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/sliding_window_rate_limit.lua"), List.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;

    public RedisRateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
    }

    public Decision tryAcquire(String policyName, Policy policy, String clientId) {
        String key = "ratelimit:" + policyName + ":" + clientId;
        try {
            List<?> result = redis.execute(SCRIPT, List.of(key),
                    String.valueOf(policy.window().toMillis()), String.valueOf(policy.limit()), UUID.randomUUID().toString());
            Decision decision = new Decision(toLong(result.get(0)) == 1, policy.limit(), toLong(result.get(1)),
                    toLong(result.get(2)));
            if (!decision.allowed()) {
                meterRegistry.counter("shortener.ratelimit.rejected", "policy", policyName).increment();
            }
            return decision;
        } catch (RuntimeException e) {
            // Fail open: an unavailable limiter must not take the whole service down with it
            log.warn("Rate limiter unavailable ({}); allowing request", e.getMessage());
            return new Decision(true, policy.limit(), policy.limit(), 0);
        }
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    public record Decision(boolean allowed, long limit, long remaining, long retryAfterMillis) {

        public long retryAfterSeconds() {
            return Math.max(1, (retryAfterMillis + 999) / 1000);
        }
    }
}
