package com.ebrahimmorkas.shortener.analytics;

import java.time.Instant;

public record LinkStatsResponse(String code, String targetUrl, long totalClicks, Instant createdAt, Instant expiresAt) {
}
