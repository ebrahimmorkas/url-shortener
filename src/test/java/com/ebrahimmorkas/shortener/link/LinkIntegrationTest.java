package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LinkIntegrationTest extends IntegrationTest {

    @Test
    void shortenReturnsCodeAndShortUrl() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://spring.io/projects/spring-boot\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.shortUrl", matchesPattern("https://sho\\.rt/[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.targetUrl").value("https://spring.io/projects/spring-boot"))
                .andExpect(header().string("Location", matchesPattern("https://sho\\.rt/[0-9A-Za-z]{7}")));
    }

    @Test
    void shortCodeRedirectsToTargetWithoutBrowserCaching() throws Exception {
        String code = shorten("https://example.com/some/page?q=1");

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/page?q=1"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void linkDetailsCanBeFetchedWithoutRedirecting() throws Exception {
        String code = shorten("https://example.com/details");

        mockMvc.perform(get("/api/links/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/details"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/zzzzzzz")).andExpect(status().isNotFound());
    }

    @Test
    void invalidUrlsAreRejected() throws Exception {
        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid URL"));

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists());
    }

    @Test
    void eachShortenRequestGetsItsOwnCode() throws Exception {
        String first = shorten("https://example.com/same");
        String second = shorten("https://example.com/same");

        assertThat(first).isNotEqualTo(second);
    }
}
