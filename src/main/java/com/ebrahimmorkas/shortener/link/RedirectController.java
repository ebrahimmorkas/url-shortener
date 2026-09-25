package com.ebrahimmorkas.shortener.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect")
public class RedirectController {

    private final LinkService linkService;

    /**
     * 302 (not 301) plus {@code no-store}: browsers must come back through us on every click, so
     * clicks can be counted and expired links stop working immediately.
     */
    @GetMapping("/{code:[A-Za-z0-9_-]{4,30}}")
    @Operation(summary = "Follow a short link")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(linkService.resolve(code)))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
