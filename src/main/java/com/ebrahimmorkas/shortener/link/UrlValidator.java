package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.common.InvalidUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Accepts only absolute http(s) URLs with a host, and refuses links back to this service (redirect loops). */
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final String ownHost;

    public UrlValidator(@Value("${app.base-url}") String baseUrl) {
        this.ownHost = URI.create(baseUrl).getHost().toLowerCase(Locale.ROOT);
    }

    public String validate(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL");
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs can be shortened");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must include a host");
        }
        if (uri.getHost().toLowerCase(Locale.ROOT).equals(ownHost)) {
            throw new InvalidUrlException("Links to this shortener are not allowed");
        }
        return uri.toString();
    }
}
