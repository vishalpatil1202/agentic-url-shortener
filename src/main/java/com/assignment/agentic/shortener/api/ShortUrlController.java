package com.assignment.agentic.shortener.api;

import com.assignment.agentic.shortener.api.dto.AnalyticsResponse;
import com.assignment.agentic.shortener.api.dto.CreateShortUrlRequest;
import com.assignment.agentic.shortener.api.dto.ShortUrlResponse;
import com.assignment.agentic.shortener.service.AnalyticsService;
import com.assignment.agentic.shortener.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;
    private final AnalyticsService analyticsService;

    public ShortUrlController(ShortUrlService shortUrlService, AnalyticsService analyticsService) {
        this.shortUrlService = shortUrlService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortUrlResponse create(@Valid @RequestBody CreateShortUrlRequest request, HttpServletRequest http) {
        return shortUrlService.create(request, http.getRemoteAddr());
    }

    @GetMapping("/urls/{code}")
    public ShortUrlResponse get(@PathVariable String code) {
        return shortUrlService.get(code);
    }

    @GetMapping("/urls/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        return analyticsService.analytics(code);
    }

    @DeleteMapping("/urls/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable String code) {
        shortUrlService.deactivate(code);
    }
}
