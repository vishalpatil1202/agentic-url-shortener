package com.assignment.agentic.orchestrator.api;

import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.ApprovalView;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.DecisionRequest;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.DecisionView;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.MetricsView;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.ReplanRequest;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.RunDetail;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.RunSummary;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.StageView;
import com.assignment.agentic.orchestrator.api.dto.OrchestratorDtos.StartRunRequest;
import com.assignment.agentic.orchestrator.definition.SdlcDefinitions;
import com.assignment.agentic.orchestrator.domain.WorkflowRun;
import com.assignment.agentic.orchestrator.engine.OrchestrationPersistence;
import com.assignment.agentic.orchestrator.engine.WorkflowEngine;
import com.assignment.agentic.orchestrator.metrics.OrchestrationMetrics;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import com.assignment.agentic.orchestrator.repo.ApprovalRepository;
import com.assignment.agentic.orchestrator.repo.DecisionRecordRepository;
import com.assignment.agentic.orchestrator.repo.StageRunRepository;
import com.assignment.agentic.orchestrator.repo.WorkflowRunRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

    private final WorkflowEngine workflowEngine;
    private final WorkflowRunRepository workflowRunRepository;
    private final StageRunRepository stageRunRepository;
    private final ApprovalRepository approvalRepository;
    private final DecisionRecordRepository decisionRecordRepository;
    private final OrchestrationPersistence persistence;
    private final OrchestrationMetrics metrics;

    public OrchestratorController(WorkflowEngine workflowEngine,
                                  WorkflowRunRepository workflowRunRepository,
                                  StageRunRepository stageRunRepository,
                                  ApprovalRepository approvalRepository,
                                  DecisionRecordRepository decisionRecordRepository,
                                  OrchestrationPersistence persistence,
                                  OrchestrationMetrics metrics) {
        this.workflowEngine = workflowEngine;
        this.workflowRunRepository = workflowRunRepository;
        this.stageRunRepository = stageRunRepository;
        this.approvalRepository = approvalRepository;
        this.decisionRecordRepository = decisionRecordRepository;
        this.persistence = persistence;
        this.metrics = metrics;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunSummary start(@Valid @RequestBody StartRunRequest request) {
        Scenario scenario = request.scenario() == null ? Scenario.GREENFIELD : request.scenario();
        AutonomyMode mode = request.autonomyMode() == null ? AutonomyMode.SUPERVISED : request.autonomyMode();
        String requirement = request.requirement() == null ? defaultRequirement(scenario) : request.requirement();
        boolean defaults = request.acceptDefaultAssumptions() != null && request.acceptDefaultAssumptions();
        return toSummary(workflowEngine.start(scenario, mode, requirement, defaults));
    }

    @GetMapping("/runs")
    public List<RunSummary> list() {
        return workflowRunRepository.findAllByOrderByStartedAtDesc().stream().map(this::toSummary).toList();
    }

    @GetMapping("/runs/{id}")
    public RunDetail get(@PathVariable String id) {
        WorkflowRun run = workflowRunRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Run not found"));
        return new RunDetail(
                toSummary(run),
                persistence.readContext(run),
                stageRunRepository.findByRunId(id).stream()
                        .map(s -> new StageView(s.getStageId(), s.getStatus().name(), s.getAttempt(), s.getStartedAt(), s.getCompletedAt(), s.getErrorMessage()))
                        .toList(),
                approvalRepository.findByRunId(id).stream()
                        .map(a -> new ApprovalView(a.getId(), a.getStageId(), a.getReason(), a.getStatus().name(), a.getDecidedBy(), a.getComment(), a.getCreatedAt()))
                        .toList(),
                decisionRecordRepository.findByRunIdOrderByCreatedAtAsc(id).stream()
                        .map(d -> new DecisionView(d.getId(), d.getActor().name(), d.getAction(), d.getRationale(), d.getCreatedAt()))
                        .toList()
        );
    }

    @PostMapping("/runs/{id}/approvals/{approvalId}")
    public RunDetail decide(@PathVariable String id,
                            @PathVariable String approvalId,
                            @RequestBody DecisionRequest request) {
        workflowEngine.decide(id, approvalId, request.approved(), request.actor(), request.comment());
        return get(id);
    }

    @PostMapping("/runs/{id}/stop")
    public RunSummary stop(@PathVariable String id) {
        workflowEngine.requestStop(id);
        return toSummary(workflowRunRepository.findById(id).orElseThrow());
    }

    @PostMapping("/runs/{id}/replan")
    public RunDetail replan(@PathVariable String id, @RequestBody ReplanRequest request) {
        workflowEngine.replan(id, request.requirement());
        return get(id);
    }

    @GetMapping("/metrics")
    public MetricsView metrics() {
        return new MetricsView(metrics.successRate(), metrics.retryCount(), metrics.rollbackCount(), metrics.meanLatencyMs());
    }

    @GetMapping("/definition")
    public Map<String, Object> definition() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", SdlcDefinitions.sdlc().id());
        body.put("name", SdlcDefinitions.sdlc().name());
        body.put("stages", SdlcDefinitions.sdlc().stages().stream().map(node -> Map.of(
                "id", node.id(),
                "name", node.name(),
                "dependsOn", node.dependsOn(),
                "parallelSafe", node.parallelSafe(),
                "highImpact", node.highImpact()
        )).toList());
        body.put("join", "testing waits for BOTH implementation and documentation (AND sync)");
        return body;
    }

    @GetMapping("/scenarios")
    public List<Map<String, Object>> scenarios() {
        return List.of(
                scenario("GREENFIELD", "Build the shortener from a clear requirement set",
                        "POST /api/v1/orchestrator/runs with scenario=GREENFIELD, autonomyMode=SUPERVISED"),
                scenario("BROWNFIELD", "Enhance the live module with custom aliases, expiry, and retention",
                        "POST /api/v1/orchestrator/runs with scenario=BROWNFIELD"),
                scenario("AMBIGUOUS", "Normalize a vague request; pause for clarification unless defaults are accepted",
                        "POST /api/v1/orchestrator/runs with scenario=AMBIGUOUS and acceptDefaultAssumptions=false")
        );
    }

    private Map<String, Object> scenario(String id, String intent, String how) {
        return Map.of("id", id, "intent", intent, "howToRun", how);
    }

    private String defaultRequirement(Scenario scenario) {
        return switch (scenario) {
            case GREENFIELD -> "Build a URL shortener with create, redirect, analytics, and reliability controls.";
            case BROWNFIELD -> "Add custom aliases, link expiry, and a retention sweeper to the existing shortener.";
            case AMBIGUOUS -> "Make something like bitly but enterprise-ready.";
        };
    }

    private RunSummary toSummary(WorkflowRun run) {
        return new RunSummary(
                run.getId(),
                run.getScenario(),
                run.getStatus().name(),
                run.getAutonomyMode(),
                run.getContextVersion(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage()
        );
    }
}
