package com.assignment.agentic.shortener.repo;

import com.assignment.agentic.shortener.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {
    long countByShortUrlId(String shortUrlId);

    long countByShortUrlIdAndClickedAtAfter(String shortUrlId, Instant after);

    List<ClickEvent> findTop100ByShortUrlIdOrderByClickedAtDesc(String shortUrlId);

    List<ClickEvent> findByShortUrlId(String shortUrlId);
}
