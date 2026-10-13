package com.assignment.agentic.shortener.service;

import com.assignment.agentic.shortener.api.ApiException;
import com.assignment.agentic.shortener.api.dto.CreateShortUrlRequest;
import com.assignment.agentic.shortener.api.dto.ShortUrlResponse;
import com.assignment.agentic.shortener.domain.ClickEvent;
import com.assignment.agentic.shortener.domain.ShortUrl;
import com.assignment.agentic.shortener.repo.ClickEventRepository;
import com.assignment.agentic.shortener.repo.ShortUrlRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
public class ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlSafetyValidator urlSafetyValidator;
    private final CodeGenerator codeGenerator;
    private final IpHasher ipHasher;

    public ShortUrlService(ShortUrlRepository shortUrlRepository,
                           ClickEventRepository clickEventRepository,
                           UrlSafetyValidator urlSafetyValidator,
                           CodeGenerator codeGenerator,
                           IpHasher ipHasher) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.urlSafetyValidator = urlSafetyValidator;
        this.codeGenerator = codeGenerator;
        this.ipHasher = ipHasher;
    }

    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request, String createdBy) {
        URI uri = urlSafetyValidator.validateAndNormalize(request.url());
        Instant now = Instant.now();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_expiry", "expiresAt must be in the future");
        }

        ShortUrl entity = new ShortUrl();
        entity.setId(UUID.randomUUID().toString());
        entity.setOriginalUrl(uri.toString());
        entity.setCreatedAt(now);
        entity.setExpiresAt(request.expiresAt());
        entity.setActive(true);
        entity.setCreatedBy(createdBy);

        if (request.alias() != null && !request.alias().isBlank()) {
            codeGenerator.validateAlias(request.alias());
            if (shortUrlRepository.existsByCode(request.alias())) {
                throw new ApiException(HttpStatus.CONFLICT, "alias_taken", "Custom alias already exists");
            }
            entity.setCode(request.alias());
            entity.setCustomAlias(true);
        } else {
            entity.setCustomAlias(false);
            entity.setCode(allocateCode());
        }
        return toResponse(shortUrlRepository.save(entity), 0);
    }

    @Transactional(readOnly = true)
    public ShortUrl resolveActive(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short URL not found"));
        if (!url.isActive() || url.isExpired(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "expired", "Short URL is inactive or expired");
        }
        return url;
    }

    @Transactional(readOnly = true)
    public ShortUrlResponse get(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short URL not found"));
        return toResponse(url, clickEventRepository.countByShortUrlId(url.getId()));
    }

    @Transactional
    public void deactivate(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short URL not found"));
        url.setActive(false);
        shortUrlRepository.save(url);
    }

    @Async
    @Transactional
    public void recordClick(String shortUrlId, String ip, String userAgent, String referrer) {
        ClickEvent event = new ClickEvent();
        event.setId(UUID.randomUUID().toString());
        event.setShortUrlId(shortUrlId);
        event.setClickedAt(Instant.now());
        event.setIpHash(ipHasher.hash(ip));
        event.setUserAgent(truncate(userAgent, 512));
        event.setReferrer(truncate(referrer, 1024));
        clickEventRepository.save(event);
    }

    private String allocateCode() {
        for (int i = 0; i < 8; i++) {
            String code = codeGenerator.nextCode();
            if (!shortUrlRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "code_collision", "Unable to allocate a unique code");
    }

    private ShortUrlResponse toResponse(ShortUrl url, long clicks) {
        return new ShortUrlResponse(
                url.getCode(),
                url.getOriginalUrl(),
                "/" + url.getCode(),
                url.isCustomAlias(),
                url.isActive(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                clicks
        );
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
