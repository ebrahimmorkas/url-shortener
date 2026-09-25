package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
@Tag(name = "Links")
public class LinkController {

    private final LinkService linkService;

    @PostMapping
    @RateLimited("create-link")
    @Operation(summary = "Shorten a URL", description = "Rate limited per client IP (default 20/minute)")
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse link = linkService.create(request);
        return ResponseEntity.created(URI.create(link.shortUrl())).body(link);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get link details without following it")
    public LinkResponse find(@PathVariable String code) {
        return linkService.find(code);
    }
}
