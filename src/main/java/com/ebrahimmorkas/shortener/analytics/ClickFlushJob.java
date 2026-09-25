package com.ebrahimmorkas.shortener.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.ebrahimmorkas.shortener.analytics.ClickCounter.COUNT_KEY_PREFIX;
import static com.ebrahimmorkas.shortener.analytics.ClickCounter.DIRTY_SET_KEY;

/**
 * Write-behind flush of click counts from Redis to PostgreSQL.
 *
 * <p>Safe with many application instances and concurrent clicks:
 * <ul>
 *   <li>{@code SPOP} hands each dirty code to exactly one instance;</li>
 *   <li>{@code GETDEL} takes the count atomically, so clicks arriving afterwards start a fresh
 *       counter (and re-mark the code dirty) instead of being lost or double-counted;</li>
 *   <li>if the database update fails, the counts are put back into Redis for the next run.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickFlushJob {

    private static final int BATCH_SIZE = 500;

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelayString = "${app.clicks.flush-interval}", initialDelayString = "${app.clicks.flush-interval}")
    public void scheduledFlush() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("Click flush failed, will retry next run: {}", e.getMessage());
        }
    }

    /** @return number of clicks moved to the database */
    public long flush() {
        long total = 0;
        List<String> codes;
        do {
            codes = redis.opsForSet().pop(DIRTY_SET_KEY, BATCH_SIZE);
            if (codes == null || codes.isEmpty()) {
                break;
            }
            total += flushBatch(codes);
        } while (codes.size() == BATCH_SIZE);
        return total;
    }

    private long flushBatch(List<String> codes) {
        List<Object[]> updates = new ArrayList<>(codes.size());
        for (String code : codes) {
            String count = redis.opsForValue().getAndDelete(COUNT_KEY_PREFIX + code);
            if (count != null) {
                updates.add(new Object[]{Long.parseLong(count), code});
            }
        }
        try {
            jdbcTemplate.batchUpdate("update links set click_count = click_count + ? where code = ?", updates);
        } catch (RuntimeException e) {
            restore(updates);
            throw e;
        }
        return updates.stream().mapToLong(u -> (Long) u[0]).sum();
    }

    private void restore(List<Object[]> updates) {
        for (Object[] update : updates) {
            String code = (String) update[1];
            redis.opsForValue().increment(COUNT_KEY_PREFIX + code, (Long) update[0]);
            redis.opsForSet().add(DIRTY_SET_KEY, code);
        }
    }
}
