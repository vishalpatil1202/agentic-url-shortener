package com.assignment.agentic.shortener.api.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        String code,
        long totalClicks,
        long clicksLast24h,
        long uniqueVisitors,
        List<ReferrerCount> topReferrers,
        List<RecentClick> recentClicks
) {
    public record ReferrerCount(String referrer, long count) {
    }

    public record RecentClick(Instant clickedAt, String referrer, String userAgent) {
    }
}
