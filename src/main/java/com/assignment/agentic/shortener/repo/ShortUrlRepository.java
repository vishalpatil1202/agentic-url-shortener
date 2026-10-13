package com.assignment.agentic.shortener.repo;

import com.assignment.agentic.shortener.domain.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, String> {
    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    List<ShortUrl> findByActiveTrueAndExpiresAtBefore(Instant now);
}
