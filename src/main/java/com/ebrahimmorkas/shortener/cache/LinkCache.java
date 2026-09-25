package com.ebrahimmorkas.shortener.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Cache-aside store for the redirect hot path.
 *
 * <p>Redis is an optimisation, not a dependency: every operation fails open. If Redis is down,
 * lookups count as misses and redirects are served from PostgreSQL.
 */
@Slf4j
@Component
public class LinkCache {

    private static final String KEY_PREFIX = "link:";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration linkTtl;
    private final Duration missingTtl;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public LinkCache(StringRedisTemplate redis, Clock clock, MeterRegistry meterRegistry,
                     @Value("${app.cache.link-ttl}") Duration linkTtl,
                     @Value("${app.cache.missing-ttl}") Duration missingTtl) {
        this.redis = redis;
        this.clock = clock;
        this.linkTtl = linkTtl;
        this.missingTtl = missingTtl;
        this.hits = meterRegistry.counter("shortener.cache.lookups", "result", "hit");
        this.misses = meterRegistry.counter("shortener.cache.lookups", "result", "miss");
        this.errors = meterRegistry.counter("shortener.cache.errors");
    }

    public Optional<CachedLink> get(String code) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + code);
            if (value == null) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(CachedLink.deserialize(value));
        } catch (RuntimeException e) {
            onError("get", code, e);
            misses.increment();
            return Optional.empty();
        }
    }

    /** Caches a link until it expires, capped at the configured TTL. */
    public void put(String code, CachedLink link) {
        Duration ttl = linkTtl;
        if (link.expiresAt() != null) {
            Duration untilExpiry = Duration.between(clock.instant(), link.expiresAt());
            if (untilExpiry.isNegative() || untilExpiry.isZero()) {
                return;
            }
            ttl = untilExpiry.compareTo(linkTtl) < 0 ? untilExpiry : linkTtl;
        }
        set(code, link.serialize(), ttl);
    }

    /** Short-lived negative entry so scans of random codes don't all reach the database. */
    public void putMissing(String code) {
        set(code, CachedLink.MISSING.serialize(), missingTtl);
    }

    public void evict(String code) {
        try {
            redis.delete(KEY_PREFIX + code);
        } catch (RuntimeException e) {
            onError("evict", code, e);
        }
    }

    private void set(String code, String value, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + code, value, ttl);
        } catch (RuntimeException e) {
            onError("put", code, e);
        }
    }

    private void onError(String operation, String code, RuntimeException e) {
        errors.increment();
        log.warn("Redis {} failed for {} ({}); falling back to database", operation, code, e.getMessage());
    }
}
