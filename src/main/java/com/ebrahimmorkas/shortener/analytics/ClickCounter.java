package com.ebrahimmorkas.shortener.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Counts clicks in Redis so the redirect path never writes to PostgreSQL. {@link ClickFlushJob}
 * periodically moves the counts into the database (write-behind).
 */
@Slf4j
@Component
public class ClickCounter {

    static final String COUNT_KEY_PREFIX = "clicks:";
    static final String DIRTY_SET_KEY = "clicks:dirty";

    /** Increment and mark dirty atomically, in one round trip. */
    private static final RedisScript<Long> RECORD_CLICK = RedisScript.of("""
            redis.call('SADD', KEYS[2], ARGV[1])
            return redis.call('INCR', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Counter redirects;
    private final Counter dropped;

    public ClickCounter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.redirects = meterRegistry.counter("shortener.redirects");
        this.dropped = meterRegistry.counter("shortener.clicks.dropped");
    }

    public void record(String code) {
        redirects.increment();
        try {
            redis.execute(RECORD_CLICK, List.of(COUNT_KEY_PREFIX + code, DIRTY_SET_KEY), code);
        } catch (RuntimeException e) {
            // Analytics are best-effort: never fail a redirect because a click couldn't be counted
            dropped.increment();
            log.warn("Could not record click for {}: {}", code, e.getMessage());
        }
    }

    /** Clicks recorded in Redis but not yet flushed to the database. */
    public long pending(String code) {
        try {
            String value = redis.opsForValue().get(COUNT_KEY_PREFIX + code);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
