package com.ebrahimmorkas.shortener.link;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String url,
        @Schema(description = "Optional vanity code, 4-30 chars of letters, digits, '-' or '_'", example = "spring-docs")
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,30}$", message = "must be 4-30 letters, digits, '-' or '_'")
        String customAlias,
        @Schema(description = "Optional expiry; the link returns 410 Gone afterwards")
        @Future Instant expiresAt) {

    public CreateLinkRequest(String url) {
        this(url, null, null);
    }
}
