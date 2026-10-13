package com.assignment.agentic.orchestrator.engine;

import com.assignment.agentic.config.AppProperties;
import com.assignment.agentic.orchestrator.agent.StageAgents;
import com.assignment.agentic.orchestrator.definition.SdlcDefinitions;
import com.assignment.agentic.orchestrator.domain.Approval;
import com.assignment.agentic.orchestrator.domain.StageRun;
import com.assignment.agentic.orchestrator.domain.WorkflowRun;
import com.assignment.agentic.orchestrator.metrics.OrchestrationMetrics;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Actor;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AgentResult;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.ApprovalStatus;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.RunStatus;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.StageNode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.StageStatus;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.WorkflowDefinition;
import com.assignment.agentic.orchestrator.policy.PolicyEngine;
import com.assignment.agentic.orchestrator.repo.ApprovalRepository;
import com.assignment.agentic.orchestrator.repo.StageRunRepository;
import com.assignment.agentic.orchestrator.repo.WorkflowRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowDefinition definition = SdlcDefinitions.sdlc();
    private final WorkflowRunRepository workflowRunRepository;
    private final StageRunRepository stageRunRepository;
    private final ApprovalRepository approvalRepository;
    private final OrchestrationPersistence persistence;
    private final StageAgents stageAgents;
    private final PolicyEngine policyEngine;
    private final OrchestrationMetrics metrics;
    private final AppProperties properties;
    private final Executor executor;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public WorkflowEngine(WorkflowRunRepository workflowRunRepository,
                          StageRunRepository stageRunRepository,
                          ApprovalRepository approvalRepository,
                          OrchestrationPersistence persistence,
                          StageAgents stageAgents,
                          PolicyEngine policyEngine,
                          OrchestrationMetrics metrics,
                          AppProperties properties,
                          @Qualifier("orchestratorExecutor") Executor executor) {
        this.workflowRunRepository = workflowRunRepository;
        this.stageRunRepository = stageRunRepository;
        this.approvalRepository = approvalRepository;
        this.persistence = persistence;
        this.stageAgents = stageAgents;
        this.policyEngine = policyEngine;
        this.metrics = metrics;
        this.properties = properties;
        this.executor = executor;
    }

    public WorkflowRun start(Scenario scenario, AutonomyMode mode, String rawRequirement, boolean acceptDefaults) {
        WorkflowRun run = new WorkflowRun();
        run.setId(UUID.randomUUID().toString());
        run.setScenario(scenario);
        run.setDefinitionId(definition.id());
        run.setStatus(RunStatus.PENDING);
        run.setAutonomyMode(mode == null ? AutonomyMode.SUPERVISED : mode);
        run.setStopRequested(false);
        run.setContextVersion(1);
        run.setStartedAt(Instant.now());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("rawRequirement", rawRequirement);
        context.put("acceptDefaultAssumptions", acceptDefaults || scenario != Scenario.AMBIGUOUS);
        context.put("scenario", scenario.name());
        persistence.writeContext(run, context);
        persistence.saveRun(run);
        for (StageNode node : definition.stages()) {
            StageRun stage = new StageRun();
            stage.setId(UUID.randomUUID().toString());
            stage.setRunId(run.getId());
            stage.setStageId(node.id());
            stage.setStatus(StageStatus.PENDING);
            stage.setAttempt(0);
            persistence.saveStage(stage);
        }
        persistence.lineage(run.getId(), Actor.ENGINE, "START",
                "Started " + scenario + " with autonomy " + run.getAutonomyMode(), context);
        metrics.runStarted();
        executor.execute(() -> pump(run.getId()));
        return run;
    }

    public void requestStop(String runId) {
        synchronized (lock(runId)) {
            WorkflowRun run = getRun(runId);
            run.setStopRequested(true);
            persistence.saveRun(run);
            persistence.lineage(runId, Actor.HUMAN, "SAFE_STOP", "Stop requested; engine will halt at the next gate", Map.of());
        }
        executor.execute(() -> pump(runId));
    }

    public void decide(String runId, String approvalId, boolean approved, String actor, String comment) {
        synchronized (lock(runId)) {
            Approval approval = approvalRepository.findById(approvalId)
                    .orElseThrow(() -> new IllegalArgumentException("Approval not found"));
            if (!approval.getRunId().equals(runId)) {
                throw new IllegalArgumentException("Approval does not belong to run");
            }
            if (approval.getStatus() != ApprovalStatus.PENDING) {
                throw new IllegalArgumentException("Approval already decided");
            }
            approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
            approval.setDecidedBy(actor == null ? "operator" : actor);
            approval.setComment(comment);
            approval.setDecidedAt(Instant.now());
            persistence.saveApproval(approval);
            persistence.lineage(runId, Actor.HUMAN, approved ? "APPROVE" : "REJECT",
                    "Human decision on " + approval.getStageId() + ": " + comment, Map.of("approvalId", approvalId));
            if (!approved) {
                WorkflowRun run = getRun(runId);
                run.setStatus(RunStatus.FAILED);
                run.setErrorMessage("Rejected at " + approval.getStageId());
                run.setCompletedAt(Instant.now());
                persistence.saveRun(run);
                metrics.runFinished(false, Duration.between(run.getStartedAt(), Instant.now()));
                return;
            }
            StageRun stage = stageRunRepository.findByRunIdAndStageId(runId, approval.getStageId()).orElseThrow();
            if (stage.getStatus() == StageStatus.AWAITING_APPROVAL) {
                stage.setStatus(StageStatus.PENDING);
                persistence.saveStage(stage);
            }
            WorkflowRun run = getRun(runId);
            Map<String, Object> context = persistence.readContext(run);
            if (approval.getStageId().equals(SdlcDefinitions.REQUIREMENTS)) {
                context.put("acceptDefaultAssumptions", true);
                persistence.writeContext(run, context);
                persistence.saveRun(run);
            }
        }
        executor.execute(() -> pump(runId));
    }

    public WorkflowRun replan(String runId, String newRequirement) {
        synchronized (lock(runId)) {
            WorkflowRun run = getRun(runId);
            Map<String, Object> context = persistence.readContext(run);
            context.put("rawRequirement", newRequirement);
            context.put("upstreamChanged", true);
            run.setContextVersion(run.getContextVersion() + 1);
            persistence.writeContext(run, context);
            for (StageRun stage : stageRunRepository.findByRunId(runId)) {
                if (!stage.getStageId().equals(SdlcDefinitions.REQUIREMENTS)) {
                    stage.setStatus(StageStatus.INVALIDATED);
                    stage.setErrorMessage("Invalidated because upstream requirements changed");
                    persistence.saveStage(stage);
                } else {
                    stage.setStatus(StageStatus.PENDING);
                    stage.setAttempt(0);
                    persistence.saveStage(stage);
                }
            }
            run.setStatus(RunStatus.RUNNING);
            run.setErrorMessage(null);
            run.setCompletedAt(null);
            run.setStopRequested(false);
            persistence.saveRun(run);
            persistence.lineage(runId, Actor.ENGINE, "REPLAN",
                    "Upstream requirements changed; downstream stages invalidated and DAG rescheduled",
                    Map.of("version", run.getContextVersion()));
        }
        executor.execute(() -> pump(runId));
        return getRun(runId);
    }

    public void pump(String runId) {
        try {
            while (true) {
                List<String> readyIds;
                synchronized (lock(runId)) {
                    readyIds = schedule(runId);
                }
                if (readyIds == null || readyIds.isEmpty()) {
                    return;
                }
                List<CompletableFuture<Void>> jobs = new ArrayList<>();
                for (String stageId : readyIds) {
                    jobs.add(CompletableFuture.runAsync(() -> executeStage(runId, stageId), executor));
                }
                CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();
            }
        } catch (Exception ex) {
            log.error("Orchestrator failed for run {}", runId, ex);
            synchronized (lock(runId)) {
                WorkflowRun run = getRun(runId);
                run.setStatus(RunStatus.FAILED);
                run.setErrorMessage(ex.getMessage());
                run.setCompletedAt(Instant.now());
                persistence.saveRun(run);
                metrics.runFinished(false, Duration.between(run.getStartedAt(), Instant.now()));
            }
        }
    }

    /**
     * @return stage ids to execute now; empty/null means waiting or terminal
     */
    private List<String> schedule(String runId) {
        WorkflowRun run = getRun(runId);
        if (isTerminal(run.getStatus()) && run.getStatus() != RunStatus.STOPPED && !run.isStopRequested()) {
            return List.of();
        }
        if (run.isStopRequested() && run.getStatus() != RunStatus.STOPPED) {
            run.setStatus(RunStatus.STOPPED);
            run.setCompletedAt(Instant.now());
            run.setErrorMessage("Safe-stop honored");
            persistence.saveRun(run);
            persistence.lineage(runId, Actor.ENGINE, "STOPPED", "Cooperative stop after current gate", Map.of());
            metrics.runFinished(false, Duration.between(run.getStartedAt(), Instant.now()));
            return List.of();
        }
        Map<String, StageRun> stages = stageRunRepository.findByRunId(runId).stream()
                .collect(Collectors.toMap(StageRun::getStageId, s -> s));
        for (StageRun stage : stages.values()) {
            if (stage.getStatus() == StageStatus.INVALIDATED) {
                stage.setStatus(StageStatus.PENDING);
                persistence.saveStage(stage);
            }
        }
        Map<String, StageRun> current = stageRunRepository.findByRunId(runId).stream()
                .collect(Collectors.toMap(StageRun::getStageId, s -> s));

        if (current.values().stream().allMatch(s -> s.getStatus() == StageStatus.SUCCEEDED)) {
            completeSuccess(run);
            return List.of();
        }

        List<String> ready = definition.stages().stream()
                .filter(node -> isReady(node, current))
                .map(StageNode::id)
                .toList();

        if (ready.isEmpty()) {
            if (current.values().stream().anyMatch(s -> s.getStatus() == StageStatus.AWAITING_APPROVAL)) {
                boolean clarify = SdlcDefinitions.REQUIREMENTS.equals(
                        current.values().stream()
                                .filter(s -> s.getStatus() == StageStatus.AWAITING_APPROVAL)
                                .map(StageRun::getStageId)
                                .findFirst()
                                .orElse(""));
                run.setStatus(clarify ? RunStatus.WAITING_CLARIFICATION : RunStatus.WAITING_APPROVAL);
                persistence.saveRun(run);
                return List.of();
            }
            if (current.values().stream().anyMatch(s -> s.getStatus() == StageStatus.FAILED || s.getStatus() == StageStatus.ROLLED_BACK)) {
                if (!isTerminal(run.getStatus())) {
                    failRun(run, "Stage failure with no remaining ready work");
                }
                return List.of();
            }
            return List.of();
        }

        run.setStatus(RunStatus.RUNNING);
        persistence.saveRun(run);
        return ready;
    }

    private void executeStage(String runId, String stageId) {
        synchronized (lock(runId)) {
            WorkflowRun run = getRun(runId);
            StageRun stage = stageRunRepository.findByRunIdAndStageId(runId, stageId).orElseThrow();
            StageNode node = definition.stage(stageId);
            Map<String, Object> context = persistence.readContext(run);

            PolicyEngine.PolicyDecision entry = policyEngine.evaluateEntry(stageId, run.getScenario(), run.getAutonomyMode(), context);
            persistence.lineage(runId, Actor.POLICY, "ENTRY_GATE", entry.reason(), entry);
            if (!entry.allowed()) {
                stage.setStatus(StageStatus.FAILED);
                stage.setErrorMessage(String.join("; ", entry.violations()));
                persistence.saveStage(stage);
                return;
            }
            if (entry.requiresHumanApproval() && !alreadyApproved(runId, stageId)) {
                createApproval(run, stage, entry.reason());
                return;
            }

            stage.setStatus(StageStatus.RUNNING);
            stage.setStartedAt(Instant.now());
            stage.setInputJson(persistence.writeJson(context));
            persistence.saveStage(stage);

            AgentResult result = null;
            Exception lastError = null;
            int max = properties.getOrchestrator().getMaxRetries();
            for (int attempt = 1; attempt <= max; attempt++) {
                stage.setAttempt(attempt);
                persistence.saveStage(stage);
                try {
                    result = stageAgents.execute(stageId, run.getScenario(), context, runId);
                    PolicyEngine.PolicyDecision exit = policyEngine.evaluateExit(stageId, result.output());
                    persistence.lineage(runId, Actor.POLICY, "EXIT_GATE", exit.reason(), exit);
                    if (result.success() && exit.allowed()) {
                        break;
                    }
                    lastError = new IllegalStateException(exit.violations().isEmpty() ? result.summary() : String.join("; ", exit.violations()));
                    result = null;
                    metrics.retry();
                    Thread.sleep(properties.getOrchestrator().getRetryBackoffMs() * attempt);
                } catch (Exception ex) {
                    lastError = ex;
                    metrics.retry();
                    try {
                        Thread.sleep(properties.getOrchestrator().getRetryBackoffMs() * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (result == null) {
                rollback(run, stage, lastError == null ? "exhausted retries" : lastError.getMessage());
                return;
            }

            context.put(stageId, result.output());
            if (stageId.equals(SdlcDefinitions.TESTING)) {
                context.put("testsPassed", result.output().get("passed"));
            }
            if (result.needsClarification()) {
                persistence.writeContext(run, context);
                persistence.saveRun(run);
                stage.setOutputJson(persistence.writeJson(result));
                persistence.saveStage(stage);
                createApproval(run, stage, "Ambiguous requirements require human clarification or accepted assumptions");
                run.setStatus(RunStatus.WAITING_CLARIFICATION);
                persistence.saveRun(run);
                persistence.lineage(runId, Actor.AGENT, "CLARIFY", result.summary(), result.output());
                return;
            }

            stage.setStatus(StageStatus.SUCCEEDED);
            stage.setCompletedAt(Instant.now());
            stage.setOutputJson(persistence.writeJson(result));
            persistence.saveStage(stage);
            persistence.writeContext(run, context);
            persistence.saveRun(run);
            persistence.lineage(runId, Actor.AGENT, "STAGE_COMPLETED", result.summary(),
                    Map.of("stage", stageId, "artifacts", result.artifacts(), "risks", result.risks(), "node", node.name()));
        }
    }

    private void rollback(WorkflowRun run, StageRun stage, String error) {
        metrics.rollback();
        Map<String, Object> context = persistence.readContext(run);
        context.remove(stage.getStageId());
        persistence.writeContext(run, context);
        persistence.saveRun(run);
        stage.setStatus(StageStatus.ROLLED_BACK);
        stage.setErrorMessage(error);
        stage.setCompletedAt(Instant.now());
        persistence.saveStage(stage);
        for (StageNode node : definition.stages()) {
            if (node.dependsOn().contains(stage.getStageId())) {
                StageRun downstream = stageRunRepository.findByRunIdAndStageId(run.getId(), node.id()).orElseThrow();
                downstream.setStatus(StageStatus.INVALIDATED);
                persistence.saveStage(downstream);
            }
        }
        persistence.lineage(run.getId(), Actor.ENGINE, "ROLLBACK",
                "Rolled back " + stage.getStageId() + " after bounded retries: " + error, Map.of());
        failRun(run, "Rolled back " + stage.getStageId() + ": " + error);
    }

    private void createApproval(WorkflowRun run, StageRun stage, String reason) {
        Approval approval = new Approval();
        approval.setId(UUID.randomUUID().toString());
        approval.setRunId(run.getId());
        approval.setStageId(stage.getStageId());
        approval.setReason(reason);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCreatedAt(Instant.now());
        persistence.saveApproval(approval);
        stage.setStatus(StageStatus.AWAITING_APPROVAL);
        persistence.saveStage(stage);
        run.setStatus(RunStatus.WAITING_APPROVAL);
        persistence.saveRun(run);
        persistence.lineage(run.getId(), Actor.ENGINE, "APPROVAL_REQUIRED", reason, Map.of("stage", stage.getStageId()));
    }

    private boolean alreadyApproved(String runId, String stageId) {
        return approvalRepository.findFirstByRunIdAndStageIdAndStatus(runId, stageId, ApprovalStatus.APPROVED).isPresent();
    }

    private boolean isReady(StageNode node, Map<String, StageRun> stages) {
        StageRun self = stages.get(node.id());
        if (self.getStatus() != StageStatus.PENDING) {
            return false;
        }
        return node.dependsOn().stream().allMatch(dep -> stages.get(dep).getStatus() == StageStatus.SUCCEEDED);
    }

    private void completeSuccess(WorkflowRun run) {
        run.setStatus(RunStatus.SUCCEEDED);
        run.setCompletedAt(Instant.now());
        persistence.saveRun(run);
        persistence.lineage(run.getId(), Actor.ENGINE, "COMPLETED", "All stages passed gates", Map.of());
        metrics.runFinished(true, Duration.between(run.getStartedAt(), Instant.now()));
    }

    private void failRun(WorkflowRun run, String message) {
        run.setStatus(RunStatus.FAILED);
        run.setErrorMessage(message);
        run.setCompletedAt(Instant.now());
        persistence.saveRun(run);
        persistence.lineage(run.getId(), Actor.ENGINE, "FAILED", message, Map.of());
        metrics.runFinished(false, Duration.between(run.getStartedAt(), Instant.now()));
    }

    private boolean isTerminal(RunStatus status) {
        return status == RunStatus.SUCCEEDED || status == RunStatus.FAILED
                || status == RunStatus.STOPPED || status == RunStatus.ROLLED_BACK;
    }

    private WorkflowRun getRun(String runId) {
        return workflowRunRepository.findById(runId).orElseThrow(() -> new IllegalArgumentException("Run not found"));
    }

    private Object lock(String runId) {
        return locks.computeIfAbsent(runId, k -> new Object());
    }
}
