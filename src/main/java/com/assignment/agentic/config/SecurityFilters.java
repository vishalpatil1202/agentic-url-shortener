package com.assignment.agentic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SecurityFilters {

    @Component
    public static class SecurityHeadersFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Cache-Control", "no-store");
            chain.doFilter(request, response);
        }
    }

    @Component
    public static class RateLimitFilter extends OncePerRequestFilter {
        private final AppProperties properties;
        private final Map<String, Window> windows = new ConcurrentHashMap<>();

        public RateLimitFilter(AppProperties properties) {
            this.properties = properties;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/v1/shorten")) {
                chain.doFilter(request, response);
                return;
            }
            String key = clientIp(request);
            Window window = windows.compute(key, (k, existing) -> {
                long now = Instant.now().getEpochSecond() / 60;
                if (existing == null || existing.minute != now) {
                    return new Window(now);
                }
                existing.count.incrementAndGet();
                return existing;
            });
            if (window.count.get() > properties.getShortener().getRateLimitPerMinute()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many shorten requests\"}");
                return;
            }
            chain.doFilter(request, response);
        }

        private String clientIp(HttpServletRequest request) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        }

        private static final class Window {
            private final long minute;
            private final AtomicInteger count = new AtomicInteger(1);

            private Window(long minute) {
                this.minute = minute;
            }
        }
    }
}
