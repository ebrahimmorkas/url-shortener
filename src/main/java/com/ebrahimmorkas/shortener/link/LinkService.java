package com.ebrahimmorkas.shortener.link;

import com.ebrahimmorkas.shortener.cache.CachedLink;
import com.ebrahimmorkas.shortener.cache.LinkCache;
import com.ebrahimmorkas.shortener.common.AliasUnavailableException;
import com.ebrahimmorkas.shortener.common.LinkExpiredException;
import com.ebrahimmorkas.shortener.common.LinkNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final AliasPolicy aliasPolicy;
    private final LinkCache linkCache;
    private final Clock clock;
    private final String baseUrl;

    public LinkService(LinkRepository linkRepository, ShortCodeGenerator codeGenerator, UrlValidator urlValidator,
                       AliasPolicy aliasPolicy, LinkCache linkCache, Clock clock,
                       @Value("${app.base-url}") String baseUrl) {
        this.linkRepository = linkRepository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.aliasPolicy = aliasPolicy;
        this.linkCache = linkCache;
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
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            // Two requests raced for the same alias; the unique constraint picked the winner
            throw new AliasUnavailableException(code, "already taken");
        }
        evictAfterCommit(code);
        return LinkResponse.from(link, baseUrl);
    }

    @Transactional(readOnly = true)
    public LinkResponse find(String code) {
        return LinkResponse.from(getLink(code), baseUrl);
    }

    /**
     * The redirect hot path: Redis first, PostgreSQL on a miss (cache-aside). Deliberately not
     * {@code @Transactional}, so a cache hit never borrows a database connection.
     */
    public String resolve(String code) {
        CachedLink link = linkCache.get(code).orElseGet(() -> loadAndCache(code));
        if (link.isMissing()) {
            throw new LinkNotFoundException(code);
        }
        if (link.isExpiredAt(clock.instant())) {
            throw new LinkExpiredException(code);
        }
        return link.targetUrl();
    }

    private CachedLink loadAndCache(String code) {
        return linkRepository.findByCode(code)
                .map(found -> {
                    CachedLink cached = new CachedLink(found.getTargetUrl(), found.getExpiresAt());
                    linkCache.put(code, cached);
                    return cached;
                })
                .orElseGet(() -> {
                    linkCache.putMissing(code);
                    return CachedLink.MISSING;
                });
    }

    private String claimAlias(String alias) {
        aliasPolicy.check(alias);
        if (linkRepository.existsByCode(alias)) {
            throw new AliasUnavailableException(alias, "already taken");
        }
        return alias;
    }

    /**
     * A probe for a not-yet-existing alias may have cached it as missing. Evicting only after
     * commit ensures a concurrent redirect can't re-cache "missing" between eviction and commit.
     */
    private void evictAfterCommit(String code) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                linkCache.evict(code);
            }
        });
    }

    private Link getLink(String code) {
        return linkRepository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }
}
