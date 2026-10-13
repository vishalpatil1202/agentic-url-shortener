package com.assignment.agentic.orchestrator.agent;

import com.assignment.agentic.orchestrator.definition.SdlcDefinitions;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AgentResult;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import com.assignment.agentic.orchestrator.store.ArtifactStore;
import com.assignment.agentic.shortener.api.ApiException;
import com.assignment.agentic.shortener.api.dto.AnalyticsResponse;
import com.assignment.agentic.shortener.api.dto.CreateShortUrlRequest;
import com.assignment.agentic.shortener.api.dto.ShortUrlResponse;
import com.assignment.agentic.shortener.service.AnalyticsService;
import com.assignment.agentic.shortener.service.RetentionService;
import com.assignment.agentic.shortener.service.ShortUrlService;
import com.assignment.agentic.shortener.service.UrlSafetyValidator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StageAgents {

    private final ArtifactStore artifactStore;
    private final ShortUrlService shortUrlService;
    private final AnalyticsService analyticsService;
    private final RetentionService retentionService;
    private final UrlSafetyValidator urlSafetyValidator;

    public StageAgents(ArtifactStore artifactStore,
                       ShortUrlService shortUrlService,
                       AnalyticsService analyticsService,
                       RetentionService retentionService,
                       UrlSafetyValidator urlSafetyValidator) {
        this.artifactStore = artifactStore;
        this.shortUrlService = shortUrlService;
        this.analyticsService = analyticsService;
        this.retentionService = retentionService;
        this.urlSafetyValidator = urlSafetyValidator;
    }

    public AgentResult execute(String stageId, Scenario scenario, Map<String, Object> context, String runId) {
        return switch (stageId) {
            case SdlcDefinitions.REQUIREMENTS -> requirements(scenario, context, runId);
            case SdlcDefinitions.ARCHITECTURE -> architecture(scenario, context, runId);
            case SdlcDefinitions.IMPLEMENTATION -> implementation(scenario, context, runId);
            case SdlcDefinitions.DOCUMENTATION -> documentation(scenario, context, runId);
            case SdlcDefinitions.TESTING -> testing(scenario, context, runId);
            case SdlcDefinitions.RELEASE -> release(scenario, context, runId);
            default -> throw new IllegalArgumentException("Unknown stage " + stageId);
        };
    }

    private AgentResult requirements(Scenario scenario, Map<String, Object> context, String runId) {
        String raw = String.valueOf(context.getOrDefault("rawRequirement", ""));
        List<String> ambiguities = new ArrayList<>();
        if (scenario == Scenario.AMBIGUOUS) {
            ambiguities.add("SLA / availability target is unspecified");
            ambiguities.add("Custom aliases vs random codes is unspecified");
            ambiguities.add("Analytics retention window is unspecified");
            ambiguities.add("Authn/authz model is unspecified");
        }
        boolean autoAssume = Boolean.TRUE.equals(context.get("acceptDefaultAssumptions"));
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("intent", scenario == Scenario.BROWNFIELD
                ? "Enhance existing shortener with custom aliases, expiry/retention, and richer analytics"
                : "Build a URL shortener with create/redirect/analytics and reliability controls");
        normalized.put("functional", List.of(
                "Create short URLs (random code or custom alias)",
                "Redirect with click capture",
                "Analytics: totals, unique visitors, referrers",
                "Deactivate and expire links"
        ));
        normalized.put("nonFunctional", List.of(
                "Block SSRF/private targets",
                "Rate-limit shorten API",
                "Hashed IPs only",
                "Collision-retry on code allocation"
        ));
        normalized.put("ambiguities", ambiguities);
        Map<String, String> assumptions = Map.of(
                "availability", "best-effort single-node prototype, not multi-AZ HA",
                "auth", "no end-user auth in prototype; operator approvals on orchestrator",
                "alias", "optional custom aliases, 3-32 charset [A-Za-z0-9_-]",
                "retention", "explicit expiresAt plus deactivate-expired job"
        );
        normalized.put("assumptions", assumptions);
        normalized.put("ambiguous", !ambiguities.isEmpty() && !autoAssume);
        normalized.put("assumptionsAccepted", autoAssume || ambiguities.isEmpty());

        String artifact = artifactStore.write(runId, SdlcDefinitions.REQUIREMENTS, "problem-statement.md",
                "Scenario: " + scenario + "\n\nRaw: " + raw + "\n\nNormalized:\n" + normalized);
        if (!ambiguities.isEmpty() && !autoAssume) {
            return new AgentResult(true, true, true,
                    "Requirements are ambiguous; human clarification required before architecture.",
                    normalized, List.of(artifact), ambiguities, null);
        }
        return AgentResult.ok("Requirements normalized with explicit assumptions.", normalized, List.of(artifact), List.of());
    }

    private AgentResult architecture(Scenario scenario, Map<String, Object> context, String runId) {
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("style", "Modular monolith (shortener + orchestrator bounded contexts)");
        design.put("persistence", "H2 file via Flyway; PostgreSQL-compatible SQL");
        design.put("apis", List.of("POST /api/v1/shorten", "GET /r/{code}", "GET /api/v1/urls/{code}", "GET analytics", "DELETE"));
        design.put("orchestration", "Explicit SDLC DAG with AND-join, entry/exit gates, approvals, retry/rollback/safe-stop");
        design.put("decisions", List.of(
                "Redirect path is /r/{code} so API routes never collide with codes",
                "Click ingest is async to keep redirect latency low",
                "IPs are HMAC-hashed; raw IPs are not stored",
                "Human approval is mandatory for release regardless of autonomy mode"
        ));
        if (scenario == Scenario.BROWNFIELD) {
            design.put("impactedModules", List.of("ShortUrlService", "CodeGenerator", "RetentionService", "AnalyticsService"));
        }
        String artifact = artifactStore.write(runId, SdlcDefinitions.ARCHITECTURE, "adr.md",
                "Architecture for " + scenario + "\n" + design);
        return AgentResult.ok("Architecture recorded with change-impact analysis.", design, List.of(artifact),
                List.of("Single-node H2 is not HA", "In-memory rate limiter does not cluster"));
    }

    private AgentResult implementation(Scenario scenario, Map<String, Object> context, String runId) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("module", "com.assignment.agentic.shortener");
        output.put("scenario", scenario.name());
        if (scenario == Scenario.BROWNFIELD) {
            Instant expiry = Instant.now().plus(2, ChronoUnit.HOURS);
            ShortUrlResponse created = shortUrlService.create(
                    new CreateShortUrlRequest("https://example.com/brownfield", "promo-" + runId.substring(0, 8), expiry),
                    "orchestrator");
            output.put("customAliasDemo", created);
            output.put("changedFiles", List.of(
                    "CodeGenerator.validateAlias",
                    "ShortUrlService.create",
                    "RetentionService.deactivateExpired"
            ));
        } else {
            ShortUrlResponse created = shortUrlService.create(
                    new CreateShortUrlRequest("https://example.com/greenfield", null, null),
                    "orchestrator");
            output.put("randomCodeDemo", created);
        }
        String artifact = artifactStore.write(runId, SdlcDefinitions.IMPLEMENTATION, "change-set.md",
                "Implementation evidence for " + scenario + "\n" + output);
        return AgentResult.ok("Implementation evidence captured against the live shortener module.", output, List.of(artifact),
                List.of("Prototype does not mutate source at runtime; it executes and records the live module"));
    }

    private AgentResult documentation(Scenario scenario, Map<String, Object> context, String runId) {
        String body = """
                # Stage documentation
                Scenario: %s
                APIs: shorten, redirect /r/{code}, metadata, analytics, deactivate.
                Orchestrator: start run, approve, stop, replan, inspect lineage.
                """.formatted(scenario);
        String artifact = artifactStore.write(runId, SdlcDefinitions.DOCUMENTATION, "runbook.md", body);
        return AgentResult.ok("Runbook generated.", Map.of("docs", "runbook.md", "scenario", scenario.name()),
                List.of(artifact), List.of());
    }

    private AgentResult testing(Scenario scenario, Map<String, Object> context, String runId) {
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        try {
            ShortUrlResponse created = shortUrlService.create(
                    new CreateShortUrlRequest("https://example.org/test-" + scenario.name().toLowerCase(), null, Instant.now().plus(1, ChronoUnit.DAYS)),
                    "test-agent");
            shortUrlService.resolveActive(created.code());
            AnalyticsResponse analytics = analyticsService.analytics(created.code());
            checks.add("create+resolve passed for " + created.code());
            checks.add("analytics reachable, clicks=" + analytics.totalClicks());
        } catch (Exception ex) {
            failures.add("happy-path failed: " + ex.getMessage());
        }
        try {
            urlSafetyValidator.validateAndNormalize("http://127.0.0.1/secret");
            failures.add("SSRF check failed to block loopback");
        } catch (ApiException ex) {
            checks.add("SSRF guard blocked loopback");
        }
        try {
            shortUrlService.create(new CreateShortUrlRequest("ftp://example.com/file", null, null), "test-agent");
            failures.add("ftp scheme was accepted");
        } catch (ApiException ex) {
            checks.add("non-http scheme rejected");
        }
        int expired = retentionService.deactivateExpired();
        checks.add("retention sweep deactivated " + expired + " rows");

        boolean passed = failures.isEmpty();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("passed", passed);
        output.put("checks", checks);
        output.put("failures", failures);
        output.put("fallbackUsed", passed ? null : "smoke-only");
        String artifact = artifactStore.write(runId, SdlcDefinitions.TESTING, "test-report.md", output.toString());
        if (!passed) {
            return new AgentResult(false, false, false, "Validation failed", output, List.of(artifact), failures, "smoke-only");
        }
        return AgentResult.ok("In-process validation passed against the live module.", output, List.of(artifact), List.of());
    }

    private AgentResult release(Scenario scenario, Map<String, Object> context, String runId) {
        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("testsPassed", Boolean.TRUE.equals(context.get("testsPassed")));
        checklist.put("securityControls", List.of("SSRF block", "rate limit", "hashed IPs", "reserved aliases"));
        checklist.put("observability", List.of("actuator health", "prometheus", "decision lineage"));
        checklist.put("rollbackPlan", "Deactivate codes; restore previous workflow context version; stop run");
        checklist.put("residualRisks", List.of("No multi-node lock on code allocation", "H2 not durable HA"));
        String artifact = artifactStore.write(runId, SdlcDefinitions.RELEASE, "release-checklist.md", checklist.toString());
        return new AgentResult(true, false, true, "Release package ready for human sign-off.", checklist, List.of(artifact),
                List.of("Human must accept residual reliability risk"), null);
    }
}
