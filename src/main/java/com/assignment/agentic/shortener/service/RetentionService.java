package com.assignment.agentic.shortener.service;

import com.assignment.agentic.shortener.domain.ShortUrl;
import com.assignment.agentic.shortener.repo.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RetentionService {

    private final ShortUrlRepository shortUrlRepository;

    public RetentionService(ShortUrlRepository shortUrlRepository) {
        this.shortUrlRepository = shortUrlRepository;
    }

    @Transactional
    public int deactivateExpired() {
        List<ShortUrl> expired = shortUrlRepository.findByActiveTrueAndExpiresAtBefore(Instant.now());
        expired.forEach(url -> url.setActive(false));
        shortUrlRepository.saveAll(expired);
        return expired.size();
    }
}
