package com.assignment.agentic.orchestrator.api.dto;

import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OrchestratorDtos {
    private OrchestratorDtos() {
    }

    public record StartRunRequest(
            Scenario scenario,
            AutonomyMode autonomyMode,
            String requirement,
            Boolean acceptDefaultAssumptions
    ) {
    }

    public record DecisionRequest(boolean approved, String actor, String comment) {
    }

    public record ReplanRequest(String requirement) {
    }

    public record RunSummary(
            String id,
            Scenario scenario,
            String status,
            AutonomyMode autonomyMode,
            int contextVersion,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
    }

    public record StageView(
            String stageId,
            String status,
            int attempt,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {
    }

    public record ApprovalView(
            String id,
            String stageId,
            String reason,
            String status,
            String decidedBy,
            String comment,
            Instant createdAt
    ) {
    }

    public record DecisionView(
            String id,
            String actor,
            String action,
            String rationale,
            Instant createdAt
    ) {
    }

    public record RunDetail(
            RunSummary run,
            Map<String, Object> context,
            List<StageView> stages,
            List<ApprovalView> approvals,
            List<DecisionView> lineage
    ) {
    }

    public record MetricsView(
            double successRate,
            double retries,
            double rollbacks,
            double meanLatencyMs
    ) {
    }
}
