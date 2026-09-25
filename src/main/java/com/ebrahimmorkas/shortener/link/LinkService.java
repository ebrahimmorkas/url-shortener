package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.common.LinkExpiredException;
import com.ebrahimmorkas.shortener.common.LinkNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final Clock clock;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository, ShortCodeGenerator codeGenerator, UrlValidator urlValidator,
                       Clock clock, @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String targetUrl = urlValidator.validate(request.url());
        long id = linkRepository.nextId();
        Link link = new Link(id, codeGenerator.generate(id), targetUrl, clock.instant(), null);
        return LinkResponse.from(linkRepository.save(link), baseUrl);
    }

    @Transactional(readOnly = true)
    public LinkResponse find(String code) {
        return LinkResponse.from(getLink(code), baseUrl);
    }

    /** Returns the destination for a redirect, rejecting unknown and expired codes. */
    @Transactional(readOnly = true)
    public String resolve(String code) {
        Link link = getLink(code);
        if (link.isExpiredAt(clock.instant())) {
            throw new LinkExpiredException(code);
        }
        return link.getTargetUrl();
    }

    private Link getLink(String code) {
        return linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }
}
