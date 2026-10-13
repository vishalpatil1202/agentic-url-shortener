package com.assignment.agentic.orchestrator.repo;

import com.assignment.agentic.orchestrator.domain.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, String> {
    List<WorkflowRun> findAllByOrderByStartedAtDesc();
}
