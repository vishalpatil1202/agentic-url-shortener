package com.assignment.agentic.orchestrator.repo;

import com.assignment.agentic.orchestrator.domain.Approval;
import com.assignment.agentic.orchestrator.domain.DecisionRecord;
import com.assignment.agentic.orchestrator.domain.StageRun;
import com.assignment.agentic.orchestrator.domain.WorkflowRun;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrchestratorRepositories {
    interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {
        List<WorkflowRun> findAllByOrderByStartedAtDesc();
    }

    interface StageRunRepository extends JpaRepository<StageRun, String> {
        List<StageRun> findByRunId(String runId);

        Optional<StageRun> findByRunIdAndStageId(String runId, String stageId);
    }

    interface ApprovalRepository extends JpaRepository<Approval, String> {
        List<Approval> findByRunId(String runId);

        Optional<Approval> findFirstByRunIdAndStageIdAndStatus(String runId, String stageId, ApprovalStatus status);
    }

    interface DecisionRecordRepository extends JpaRepository<DecisionRecord, String> {
        List<DecisionRecord> findByRunIdOrderByCreatedAtAsc(String runId);
    }
}
