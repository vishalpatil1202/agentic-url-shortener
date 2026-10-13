package com.assignment.agentic.shortener;

import com.assignment.agentic.shortener.api.dto.CreateShortUrlRequest;
import com.assignment.agentic.shortener.api.dto.ShortUrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ShortUrlApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createRedirectAndAnalytics() {
        ResponseEntity<ShortUrlResponse> created = rest.postForEntity(
                "/api/v1/shorten",
                new CreateShortUrlRequest("https://example.com/assignment", null, null),
                ShortUrlResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        String code = created.getBody().code();
        assertFalse(created.getBody().customAlias());

        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/assignment"));

        ResponseEntity<Map> meta = rest.getForEntity("/api/v1/urls/" + code, Map.class);
        assertEquals(HttpStatus.OK, meta.getStatusCode());
        assertTrue(meta.getBody().containsKey("originalUrl"));
    }

    @Test
    void customAliasConflict() {
        rest.postForEntity("/api/v1/shorten",
                new CreateShortUrlRequest("https://example.com/a", "launch", null),
                ShortUrlResponse.class);
        ResponseEntity<Map> dup = rest.postForEntity("/api/v1/shorten",
                new CreateShortUrlRequest("https://example.com/b", "launch", null),
                Map.class);
        assertEquals(HttpStatus.CONFLICT, dup.getStatusCode());
    }

    @Test
    void blocksPrivateTargets() {
        ResponseEntity<Map> blocked = rest.postForEntity("/api/v1/shorten",
                new CreateShortUrlRequest("http://localhost/admin", null, null),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, blocked.getStatusCode());
    }
}
