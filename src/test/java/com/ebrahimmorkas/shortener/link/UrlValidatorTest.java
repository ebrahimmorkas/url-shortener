package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.common.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator("https://sho.rt");

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a/b?c=d#e", "  https://example.com/x  "})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(validator.validate(url)).startsWith("http");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "javascript:alert(1)", "example.com", "mailto:a@b.c", "http:///path"})
    void rejectsNonWebOrHostlessUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLinksBackToTheShortener() {
        assertThatThrownBy(() -> validator.validate("https://SHO.RT/abc1234"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsMalformedUrls() {
        assertThatThrownBy(() -> validator.validate("https://exa mple.com")).isInstanceOf(InvalidUrlException.class);
    }
}
