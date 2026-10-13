package com.assignment.agentic.orchestrator.repo;

import com.assignment.agentic.orchestrator.domain.Approval;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, String> {
    List<Approval> findByRunId(String runId);

    Optional<Approval> findFirstByRunIdAndStageIdAndStatus(String runId, String stageId, ApprovalStatus status);
}
