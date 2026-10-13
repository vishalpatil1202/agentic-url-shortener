package com.assignment.agentic.orchestrator.policy;

import com.assignment.agentic.orchestrator.definition.SdlcDefinitions;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PolicyEngine {

    public record PolicyDecision(boolean allowed, List<String> violations, boolean requiresHumanApproval, String reason) {
    }

    public PolicyDecision evaluateEntry(String stageId, Scenario scenario, AutonomyMode mode, Map<String, Object> context) {
        List<String> violations = new ArrayList<>();
        if (Boolean.TRUE.equals(context.get("stopRequested"))) {
            violations.add("safe-stop is active");
        }
        if (stageId.equals(SdlcDefinitions.IMPLEMENTATION) && context.get("architecture") == null) {
            violations.add("implementation cannot start without architecture");
        }
        if (stageId.equals(SdlcDefinitions.RELEASE) && !Boolean.TRUE.equals(context.get("testsPassed"))) {
            violations.add("release blocked until tests pass");
        }
        boolean approval = requiresApproval(stageId, scenario, mode, context);
        String reason = approval ? approvalReason(stageId, scenario) : "entry gate passed";
        return new PolicyDecision(violations.isEmpty(), violations, approval, reason);
    }

    public PolicyDecision evaluateExit(String stageId, Map<String, Object> stageOutput) {
        List<String> violations = new ArrayList<>();
        if (stageOutput == null || stageOutput.isEmpty()) {
            violations.add("stage produced no output");
        }
        if (stageId.equals(SdlcDefinitions.TESTING) && !Boolean.TRUE.equals(stageOutput.get("passed"))) {
            violations.add("testing exit gate requires passed=true");
        }
        if (stageId.equals(SdlcDefinitions.REQUIREMENTS) && Boolean.TRUE.equals(stageOutput.get("ambiguous"))
                && !Boolean.TRUE.equals(stageOutput.get("assumptionsAccepted"))) {
            return new PolicyDecision(true, List.of(), true, "exit paused for clarification");
        }
        return new PolicyDecision(violations.isEmpty(), violations, false, violations.isEmpty() ? "exit gate passed" : "exit gate failed");
    }

    public boolean requiresApproval(String stageId, Scenario scenario, AutonomyMode mode, Map<String, Object> context) {
        if (mode == AutonomyMode.MANUAL) {
            return true;
        }
        if (stageId.equals(SdlcDefinitions.RELEASE)) {
            return true;
        }
        if (stageId.equals(SdlcDefinitions.ARCHITECTURE) && mode == AutonomyMode.SUPERVISED) {
            return true;
        }
        if (stageId.equals(SdlcDefinitions.IMPLEMENTATION) && scenario == Scenario.BROWNFIELD && mode != AutonomyMode.FULL_AUTO) {
            return true;
        }
        return Boolean.TRUE.equals(context.get("forceApproval"));
    }

    private String approvalReason(String stageId, Scenario scenario) {
        return switch (stageId) {
            case SdlcDefinitions.RELEASE -> "Change-control: production release requires human sign-off";
            case SdlcDefinitions.ARCHITECTURE -> "Design review: high-impact architecture decisions need oversight";
            case SdlcDefinitions.IMPLEMENTATION -> "Brownfield change-control: mutating a live system needs approval (" + scenario + ")";
            default -> "Autonomy policy requires a human checkpoint for " + stageId;
        };
    }
}
