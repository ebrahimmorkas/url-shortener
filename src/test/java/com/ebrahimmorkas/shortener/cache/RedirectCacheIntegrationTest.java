package com.ebrahimmorkas.shortener.cache;

import com.ebrahimmorkas.shortener.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectCacheIntegrationTest extends IntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void redirectIsServedFromRedisOnceCached() throws Exception {
        String code = shorten("https://example.com/cached");
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        assertThat(redis.hasKey("link:" + code)).isTrue();

        // Remove the row: the next redirect can only succeed if it comes from the cache
        jdbcTemplate.update("delete from links where code = ?", code);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/cached"));
    }

    @Test
    void unknownCodesAreNegativelyCachedWithShortTtl() throws Exception {
        String code = "nope-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(get("/{code}", code)).andExpect(status().isNotFound());

        assertThat(redis.opsForValue().get("link:" + code)).isEqualTo("!");
        assertThat(redis.getExpire("link:" + code)).isBetween(1L, 60L);
    }

    @Test
    void claimingAPreviouslyProbedAliasEvictsTheNegativeEntry() throws Exception {
        String alias = "fresh-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(get("/{code}", alias)).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/new\", \"customAlias\": \"%s\"}".formatted(alias)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/{code}", alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/new"));
    }
}
