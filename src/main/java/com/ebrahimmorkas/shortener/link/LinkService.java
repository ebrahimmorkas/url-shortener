package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.common.AliasUnavailableException;
import com.ebrahimmorkas.shortener.common.LinkExpiredException;
import com.ebrahimmorkas.shortener.common.LinkNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AliasPolicy aliasPolicy;
    private final Clock clock;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository, ShortCodeGenerator codeGenerator, UrlValidator urlValidator,
                       AliasPolicy aliasPolicy, Clock clock, @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.aliasPolicy = aliasPolicy;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public LinkResponse create(CreateLinkRequest request) {
        String targetUrl = urlValidator.validate(request.url());
        long id = linkRepository.nextId();
        String code = request.customAlias() == null ? codeGenerator.generate(id) : claimAlias(request.customAlias());
        Link link = new Link(id, code, targetUrl, clock.instant(), request.expiresAt());
        try {
            return LinkResponse.from(linkRepository.saveAndFlush(link), baseUrl);
        } catch (DataIntegrityViolationException e) {
            // Two requests raced for the same alias; the unique constraint picked the winner
            throw new AliasUnavailableException(code, "already taken");
        }
    }

    private String claimAlias(String alias) {
        aliasPolicy.check(alias);
        if (linkRepository.existsByCode(alias)) {
            throw new AliasUnavailableException(alias, "already taken");
        }
        return alias;
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
