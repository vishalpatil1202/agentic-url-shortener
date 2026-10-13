package com.assignment.agentic.orchestrator;

import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.ApprovalView;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.DecisionRequest;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.RunDetail;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.RunSummary;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.StartRunRequest;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowEngineIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void greenfieldFullAutoStopsOnlyForReleaseApprovalThenSucceeds() {
        RunSummary started = start(Scenario.GREENFIELD, AutonomyMode.FULL_AUTO, false);
        RunDetail waiting = awaitStatus(started.id(), "WAITING_APPROVAL");
        assertTrue(waiting.stages().stream().anyMatch(s -> s.stageId().equals("release_readiness")));
        approveAll(waiting);
        RunDetail done = awaitStatus(started.id(), "SUCCEEDED");
        assertTrue(done.lineage().stream().anyMatch(d -> d.action().equals("COMPLETED")));
        assertTrue(done.stages().stream().allMatch(s -> s.status().equals("SUCCEEDED")));
    }

    @Test
    void ambiguousRequiresClarificationUntilAssumptionsAccepted() {
        RunSummary started = start(Scenario.AMBIGUOUS, AutonomyMode.FULL_AUTO, false);
        RunDetail waiting = awaitStatus(started.id(), "WAITING_CLARIFICATION");
        assertTrue(waiting.approvals().stream().anyMatch(a -> a.stageId().equals("requirements") && a.status().equals("PENDING")));
        approveAll(waiting);
        RunDetail after = awaitStatus(started.id(), "WAITING_APPROVAL");
        assertTrue(after.stages().stream().anyMatch(s -> s.stageId().equals("architecture") && s.status().equals("SUCCEEDED")
                || s.stageId().equals("release_readiness")));
        approveAll(after);
        awaitStatus(started.id(), "SUCCEEDED");
    }

    @Test
    void dagDefinitionExposesJoinSemantics() {
        ResponseEntity<Map> def = rest.getForEntity("/api/v1/orchestrator/definition", Map.class);
        assertEquals(HttpStatus.OK, def.getStatusCode());
        assertTrue(String.valueOf(def.getBody().get("join")).contains("AND"));
    }

    private RunSummary start(Scenario scenario, AutonomyMode mode, boolean defaults) {
        ResponseEntity<RunSummary> response = rest.postForEntity(
                "/api/v1/orchestrator/runs",
                new StartRunRequest(scenario, mode, "test requirement", defaults),
                RunSummary.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        return response.getBody();
    }

    private RunDetail awaitStatus(String id, String status) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> {
            RunDetail detail = rest.getForEntity("/api/v1/orchestrator/runs/" + id, RunDetail.class).getBody();
            return detail != null && status.equals(detail.run().status());
        });
        return rest.getForEntity("/api/v1/orchestrator/runs/" + id, RunDetail.class).getBody();
    }

    private void approveAll(RunDetail detail) {
        for (ApprovalView approval : detail.approvals()) {
            if ("PENDING".equals(approval.status())) {
                rest.postForEntity(
                        "/api/v1/orchestrator/runs/" + detail.run().id() + "/approvals/" + approval.id(),
                        new DecisionRequest(true, "tester", "ok"),
                        RunDetail.class);
            }
        }
    }
}
