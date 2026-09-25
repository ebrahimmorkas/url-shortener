package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomAliasAndExpiryIntegrationTest extends IntegrationTest {

    @Autowired
    private LinkRepository linkRepository;

    @Test
    void customAliasBecomesTheShortCode() throws Exception {
        String alias = "docs-" + UUID.randomUUID().toString().substring(0, 8);

        create("{\"url\": \"https://docs.spring.io\", \"customAlias\": \"%s\"}".formatted(alias))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(alias))
                .andExpect(jsonPath("$.shortUrl").value("https://sho.rt/" + alias));

        mockMvc.perform(get("/{code}", alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://docs.spring.io"));
    }

    @Test
    void takenAliasIsRejected() throws Exception {
        String alias = "taken-" + UUID.randomUUID().toString().substring(0, 8);
        create("{\"url\": \"https://a.example\", \"customAlias\": \"%s\"}".formatted(alias)).andExpect(status().isCreated());

        create("{\"url\": \"https://b.example\", \"customAlias\": \"%s\"}".formatted(alias))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already taken")));
    }

    @Test
    void aliasesShapedLikeGeneratedCodesAreReserved() throws Exception {
        create("{\"url\": \"https://a.example\", \"customAlias\": \"abc1234\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("reserved for generated codes")));
    }

    @Test
    void invalidAliasCharactersAreRejected() throws Exception {
        create("{\"url\": \"https://a.example\", \"customAlias\": \"no spaces!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customAlias").exists());
    }

    @Test
    void expiryMustBeInTheFuture() throws Exception {
        create("{\"url\": \"https://a.example\", \"expiresAt\": \"%s\"}".formatted(Instant.now().minusSeconds(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.expiresAt").exists());
    }

    @Test
    void expiredLinkReturns410Gone() throws Exception {
        long id = linkRepository.nextId();
        String code = "old-" + id;
        linkRepository.save(new Link(id, code, "https://expired.example", Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS)));

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Link expired"));
    }

    @Test
    void linkWorksUntilItExpires() throws Exception {
        String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String body = create("{\"url\": \"https://soon.example\", \"expiresAt\": \"%s\"}".formatted(expiresAt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/{code}", objectMapper.readTree(body).get("code").asText()))
                .andExpect(status().isFound());
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
