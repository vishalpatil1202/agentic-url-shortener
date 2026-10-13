package com.assignment.agentic.shortener.service;

import com.assignment.agentic.config.AppProperties;
import com.assignment.agentic.shortener.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlSafetyValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "metadata.google.internal", "ip6-localhost"
    );

    private final AppProperties properties;

    public UrlSafetyValidator(AppProperties properties) {
        this.properties = properties;
    }

    public URI validateAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > properties.getShortener().getMaxUrlLength()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL exceeds max length");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL is not parseable");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "Only http/https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "URL host is required");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".internal")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsafe_url", "Target host is not allowed");
        }
        if (isPrivateOrLocal(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsafe_url", "Private or link-local targets are blocked");
        }
        return uri.normalize();
    }

    public boolean isPrivateOrLocal(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ex) {
            return false;
        }
    }
}
