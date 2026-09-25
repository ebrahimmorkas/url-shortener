package com.ebrahimmorkas.shortener.ratelimit;

import com.ebrahimmorkas.shortener.ratelimit.RateLimitProperties.Policy;
import com.ebrahimmorkas.shortener.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.base-url=https://sho.rt",
        "app.clicks.flush-interval=1h",
        "app.rate-limit.policies.create-link.limit=3",
        "app.rate-limit.policies.create-link.window=1m"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    void createLinkIsLimitedPerClientWithStandardHeaders() throws Exception {
        String client = "10.0.0." + (int) (Math.random() * 200);

        for (int remaining = 2; remaining >= 0; remaining--) {
            mockMvc.perform(create(client))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(remaining)));
        }

        mockMvc.perform(create(client))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Too many requests"));

        // A different client has its own budget
        mockMvc.perform(create("10.0.1.1")).andExpect(status().isCreated());
    }

    @Test
    void limitHoldsUnderConcurrentRequests() throws Exception {
        Policy policy = new Policy(10, Duration.ofMinutes(1));
        String client = "burst-" + UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 50; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return rateLimiter.tryAcquire("test", policy, client).allowed();
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                allowed += result.get() ? 1 : 0;
            }
            assertThat(allowed).isEqualTo(10);
        }
    }

    @Test
    void windowSlidesSoCapacityReturns() throws Exception {
        Policy policy = new Policy(2, Duration.ofMillis(500));
        String client = "slide-" + UUID.randomUUID();

        assertThat(rateLimiter.tryAcquire("test", policy, client).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire("test", policy, client).allowed()).isTrue();
        RedisRateLimiter.Decision rejected = rateLimiter.tryAcquire("test", policy, client);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterMillis()).isBetween(1L, 500L);

        Thread.sleep(600);

        assertThat(rateLimiter.tryAcquire("test", policy, client).allowed()).isTrue();
    }

    private static MockHttpServletRequestBuilder create(String clientIp) {
        return post("/api/links")
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://example.com/%s\"}".formatted(UUID.randomUUID()));
    }
}
