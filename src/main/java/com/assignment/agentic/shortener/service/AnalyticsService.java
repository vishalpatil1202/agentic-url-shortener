package com.assignment.agentic.shortener.service;

import com.assignment.agentic.shortener.api.ApiException;
import com.assignment.agentic.shortener.api.dto.AnalyticsResponse;
import com.assignment.agentic.shortener.domain.ClickEvent;
import com.assignment.agentic.shortener.domain.ShortUrl;
import com.assignment.agentic.shortener.repo.ClickEventRepository;
import com.assignment.agentic.shortener.repo.ShortUrlRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ShortUrlRepository shortUrlRepository, ClickEventRepository clickEventRepository) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String code) {
        ShortUrl url = shortUrlRepository.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short URL not found"));
        Instant dayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        long total = clickEventRepository.countByShortUrlId(url.getId());
        long last24h = clickEventRepository.countByShortUrlIdAndClickedAtAfter(url.getId(), dayAgo);
        List<ClickEvent> all = clickEventRepository.findByShortUrlId(url.getId());
        long uniqueVisitors = all.stream().map(ClickEvent::getIpHash).filter(v -> v != null).distinct().count();
        List<AnalyticsResponse.ReferrerCount> topReferrers = all.stream()
                .collect(Collectors.groupingBy(e -> e.getReferrer() == null || e.getReferrer().isBlank() ? "(direct)" : e.getReferrer(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> new AnalyticsResponse.ReferrerCount(e.getKey(), e.getValue()))
                .toList();
        List<AnalyticsResponse.RecentClick> recent = clickEventRepository.findTop100ByShortUrlIdOrderByClickedAtDesc(url.getId())
                .stream()
                .map(e -> new AnalyticsResponse.RecentClick(e.getClickedAt(), e.getReferrer(), e.getUserAgent()))
                .toList();
        return new AnalyticsResponse(url.getCode(), total, last24h, uniqueVisitors, topReferrers, recent);
    }
}
