package com.assignment.agentic.orchestrator.domain;

import com.assignment.agentic.orchestrator.model.OrchestrationModels.AutonomyMode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.RunStatus;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Scenario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Scenario scenario;

    @Column(name = "definition_id", nullable = false, length = 64)
    private String definitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "autonomy_mode", nullable = false, length = 32)
    private AutonomyMode autonomyMode;

    @Lob
    @Column(name = "context_json", nullable = false)
    private String contextJson;

    @Column(name = "stop_requested", nullable = false)
    private boolean stopRequested;

    @Column(name = "context_version", nullable = false)
    private int contextVersion = 1;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public AutonomyMode getAutonomyMode() {
        return autonomyMode;
    }

    public void setAutonomyMode(AutonomyMode autonomyMode) {
        this.autonomyMode = autonomyMode;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void setStopRequested(boolean stopRequested) {
        this.stopRequested = stopRequested;
    }

    public int getContextVersion() {
        return contextVersion;
    }

    public void setContextVersion(int contextVersion) {
        this.contextVersion = contextVersion;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
