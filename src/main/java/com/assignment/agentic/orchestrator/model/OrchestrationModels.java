package com.assignment.agentic.orchestrator.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrchestrationModels {
    private OrchestrationModels() {
    }

    public enum Scenario {
        GREENFIELD, BROWNFIELD, AMBIGUOUS
    }

    public enum AutonomyMode {
        FULL_AUTO,
        SUPERVISED,
        MANUAL
    }

    public enum RunStatus {
        PENDING,
        RUNNING,
        WAITING_APPROVAL,
        WAITING_CLARIFICATION,
        SUCCEEDED,
        FAILED,
        ROLLED_BACK,
        STOPPED
    }

    public enum StageStatus {
        PENDING,
        BLOCKED,
        RUNNING,
        AWAITING_APPROVAL,
        SUCCEEDED,
        FAILED,
        ROLLED_BACK,
        SKIPPED,
        INVALIDATED
    }

    public enum Actor {
        AGENT, HUMAN, POLICY, ENGINE
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }

    public record StageNode(
            String id,
            String name,
            Set<String> dependsOn,
            boolean parallelSafe,
            boolean highImpact,
            boolean clarificationGate
    ) {
    }

    public record WorkflowDefinition(
            String id,
            String name,
            List<StageNode> stages
    ) {
        public StageNode stage(String id) {
            return stages.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
        }
    }

    public record AgentResult(
            boolean success,
            boolean needsClarification,
            boolean needsApproval,
            String summary,
            Map<String, Object> output,
            List<String> artifacts,
            List<String> risks,
            String fallbackUsed
    ) {
        public static AgentResult ok(String summary, Map<String, Object> output, List<String> artifacts, List<String> risks) {
            return new AgentResult(true, false, false, summary, output, artifacts, risks, null);
        }
    }
}
