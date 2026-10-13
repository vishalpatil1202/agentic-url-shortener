package com.assignment.agentic.orchestrator;

import com.assignment.agentic.orchestrator.definition.SdlcDefinitions;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import com.assignment.agentic.orchestrator.policy.PolicyEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {

    private final PolicyEngine policyEngine = new PolicyEngine();

    @Test
    void releaseAlwaysNeedsHuman() {
        assertTrue(policyEngine.requiresApproval(SdlcDefinitions.RELEASE, Scenario.GREENFIELD, AutonomyMode.FULL_AUTO, Map.of()));
    }

    @Test
    void testingExitRequiresPass() {
        var failed = policyEngine.evaluateExit(SdlcDefinitions.TESTING, Map.of("passed", false));
        assertFalse(failed.allowed());
        var ok = policyEngine.evaluateExit(SdlcDefinitions.TESTING, Map.of("passed", true));
        assertTrue(ok.allowed());
    }

    @Test
    void architectureIsSupervisedHighImpact() {
        assertTrue(policyEngine.requiresApproval(SdlcDefinitions.ARCHITECTURE, Scenario.GREENFIELD, AutonomyMode.SUPERVISED, Map.of()));
        assertFalse(policyEngine.requiresApproval(SdlcDefinitions.ARCHITECTURE, Scenario.GREENFIELD, AutonomyMode.FULL_AUTO, Map.of()));
    }
}
