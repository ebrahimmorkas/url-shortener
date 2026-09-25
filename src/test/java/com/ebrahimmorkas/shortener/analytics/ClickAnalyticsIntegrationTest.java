package com.ebrahimmorkas.shortener.analytics;

import com.ebrahimmorkas.shortener.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClickAnalyticsIntegrationTest extends IntegrationTest {

    @Autowired
    private ClickFlushJob flushJob;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void clicksAreBufferedInRedisThenFlushedToPostgres() throws Exception {
        String code = shorten("https://example.com/popular");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        }

        assertThat(dbClicks(code)).isZero();
        mockMvc.perform(get("/api/links/{code}/stats", code)).andExpect(jsonPath("$.totalClicks").value(3));

        flushJob.flush();

        assertThat(dbClicks(code)).isEqualTo(3);
        assertThat(redis.hasKey("clicks:" + code)).isFalse();
        mockMvc.perform(get("/api/links/{code}/stats", code)).andExpect(jsonPath("$.totalClicks").value(3));
    }

    @Test
    void concurrentClicksAndFlushesNeverLoseOrDoubleCount() throws Exception {
        String code = shorten("https://example.com/viral");
        int clicks = 300;

        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < clicks; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
                    return null;
                }));
            }
            // Flush repeatedly while clicks are still arriving
            futures.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < 20; i++) {
                    flushJob.flush();
                    Thread.sleep(5);
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }
        flushJob.flush();

        assertThat(dbClicks(code)).isEqualTo(clicks);
    }

    @Test
    void statsForUnknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/api/links/{code}/stats", "missing-code")).andExpect(status().isNotFound());
    }

    private long dbClicks(String code) {
        return jdbcTemplate.queryForObject("select click_count from links where code = ?", Long.class, code);
    }
}
