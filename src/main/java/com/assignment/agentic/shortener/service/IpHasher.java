package com.assignment.agentic.shortener.service;

import com.assignment.agentic.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
public class IpHasher {

    private final AppProperties properties;

    public IpHasher(AppProperties properties) {
        this.properties = properties;
    }

    public String hash(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getShortener().getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash IP", ex);
        }
    }
}
