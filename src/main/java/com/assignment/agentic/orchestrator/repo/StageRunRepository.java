package com.assignment.agentic.orchestrator.repo;

import com.assignment.agentic.orchestrator.domain.StageRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageRunRepository extends JpaRepository<StageRun, String> {
    List<StageRun> findByRunId(String runId);

    Optional<StageRun> findByRunIdAndStageId(String runId, String stageId);
}
