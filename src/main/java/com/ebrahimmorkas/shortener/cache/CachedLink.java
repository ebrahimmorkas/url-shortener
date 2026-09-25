package com.ebrahimmorkas.shortener.cache;

import java.time.Instant;

/**
 * What the redirect path needs, in a compact Redis-friendly form. {@link #MISSING} represents
 * a code known not to exist (negative caching).
 */
public record CachedLink(String targetUrl, Instant expiresAt) {

    public static final CachedLink MISSING = new CachedLink(null, null);

    private static final String MISSING_MARKER = "!";
    private static final char SEPARATOR = '|';

    public boolean isMissing() {
        return targetUrl == null;
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** {@code <expiresAtEpochMillis or empty>|<targetUrl>}; the URL goes last because it may contain '|'. */
    String serialize() {
        if (isMissing()) {
            return MISSING_MARKER;
        }
        return (expiresAt == null ? "" : String.valueOf(expiresAt.toEpochMilli())) + SEPARATOR + targetUrl;
    }

    static CachedLink deserialize(String value) {
        if (MISSING_MARKER.equals(value)) {
            return MISSING;
        }
        int separator = value.indexOf(SEPARATOR);
        String expiry = value.substring(0, separator);
        return new CachedLink(value.substring(separator + 1),
                expiry.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(expiry)));
    }
}
