package com.assignment.agentic.orchestrator.repo;

import com.assignment.agentic.orchestrator.domain.DecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, String> {
    List<DecisionRecord> findByRunIdOrderByCreatedAtAsc(String runId);
}
