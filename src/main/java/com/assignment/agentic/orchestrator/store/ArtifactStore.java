package com.assignment.agentic.orchestrator.store;

import com.assignment.agentic.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
public class ArtifactStore {

    private final AppProperties properties;

    public ArtifactStore(AppProperties properties) {
        this.properties = properties;
    }

    public String write(String runId, String stageId, String filename, String content) {
        try {
            Path dir = Path.of(properties.getOrchestrator().getArtifactDir(), runId, stageId);
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            String body = "# generatedAt: " + Instant.now() + "\n\n" + content;
            Files.writeString(file, body, StandardCharsets.UTF_8);
            return file.toAbsolutePath().normalize().toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to persist artifact " + filename, ex);
        }
    }
}
