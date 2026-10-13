package com.assignment.agentic.shortener.service;

import com.assignment.agentic.config.AppProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CodeGenerator {

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public CodeGenerator(AppProperties properties) {
        this.properties = properties;
    }

    public String nextCode() {
        int length = properties.getShortener().getCodeLength();
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }

    public void validateAlias(String alias) {
        if (alias == null || alias.length() < 3 || alias.length() > 32) {
            throw new IllegalArgumentException("Alias must be 3-32 characters");
        }
        if (!alias.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Alias may contain letters, digits, underscore, and hyphen only");
        }
        if (alias.equalsIgnoreCase("api") || alias.equalsIgnoreCase("actuator") || alias.equalsIgnoreCase("swagger-ui")) {
            throw new IllegalArgumentException("Alias is reserved");
        }
    }
}
