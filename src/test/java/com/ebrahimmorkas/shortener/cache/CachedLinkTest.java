package com.ebrahimmorkas.shortener.cache;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CachedLinkTest {

    @Test
    void roundTripsLinkWithExpiry() {
        CachedLink link = new CachedLink("https://example.com/a|b?c=1", Instant.ofEpochMilli(1_800_000_000_000L));

        assertThat(CachedLink.deserialize(link.serialize())).isEqualTo(link);
    }

    @Test
    void roundTripsLinkWithoutExpiry() {
        CachedLink link = new CachedLink("https://example.com", null);

        assertThat(CachedLink.deserialize(link.serialize())).isEqualTo(link);
    }

    @Test
    void roundTripsMissingMarker() {
        assertThat(CachedLink.deserialize(CachedLink.MISSING.serialize()).isMissing()).isTrue();
    }
}
