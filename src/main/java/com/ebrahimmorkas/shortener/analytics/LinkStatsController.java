package com.ebrahimmorkas.shortener.analytics;

import com.ebrahimmorkas.shortener.common.LinkNotFoundException;
import com.ebrahimmorkas.shortener.link.Link;
import com.ebrahimmorkas.shortener.link.LinkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class LinkStatsController {

    private final LinkRepository linkRepository;
    private final ClickCounter clickCounter;

    @GetMapping("/api/links/{code}/stats")
    @Operation(summary = "Click statistics (flushed count plus clicks still buffered in Redis)")
    public LinkStatsResponse stats(@PathVariable String code) {
        Link link = linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
        return new LinkStatsResponse(link.getCode(), link.getTargetUrl(),
                link.getClickCount() + clickCounter.pending(code), link.getCreatedAt(), link.getExpiresAt());
    }
}
