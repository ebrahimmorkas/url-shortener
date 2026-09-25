package com.ebrahimmorkas.shortener.link;

import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt, Instant expiresAt) {

    static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(link.getCode(), baseUrl + "/" + link.getCode(), link.getTargetUrl(),
                link.getCreatedAt(), link.getExpiresAt());
    }
}
