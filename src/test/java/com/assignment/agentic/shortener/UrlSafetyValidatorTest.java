package com.assignment.agentic.shortener;

import com.assignment.agentic.config.AppProperties;
import com.assignment.agentic.shortener.api.ApiException;
import com.assignment.agentic.shortener.service.UrlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlSafetyValidatorTest {

    private UrlSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UrlSafetyValidator(new AppProperties());
    }

    @Test
    void acceptsPublicHttps() {
        assertEquals("https", validator.validateAndNormalize("https://example.com/a").getScheme());
    }

    @Test
    void rejectsLoopback() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateAndNormalize("http://127.0.0.1/secret"));
        assertEquals("unsafe_url", ex.getCode());
    }

    @Test
    void rejectsNonHttp() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateAndNormalize("ftp://example.com/file"));
        assertTrue(ex.getMessage().contains("http/https"));
    }
}
