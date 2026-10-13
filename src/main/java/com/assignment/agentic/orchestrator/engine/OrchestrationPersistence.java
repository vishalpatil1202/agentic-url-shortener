package com.assignment.agentic.orchestrator.engine;

import com.assignment.agentic.orchestrator.domain.Approval;
import com.assignment.agentic.orchestrator.domain.DecisionRecord;
import com.assignment.agentic.orchestrator.domain.StageRun;
import com.assignment.agentic.orchestrator.domain.WorkflowRun;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.Actor;
import com.assignment.agentic.orchestrator.repo.ApprovalRepository;
import com.assignment.agentic.orchestrator.repo.DecisionRecordRepository;
import com.assignment.agentic.orchestrator.repo.StageRunRepository;
import com.assignment.agentic.orchestrator.repo.WorkflowRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrchestrationPersistence {

    private final WorkflowRunRepository workflowRunRepository;
    private final StageRunRepository stageRunRepository;
    private final ApprovalRepository approvalRepository;
    private final DecisionRecordRepository decisionRecordRepository;
    private final ObjectMapper objectMapper;

    public OrchestrationPersistence(WorkflowRunRepository workflowRunRepository,
                                    StageRunRepository stageRunRepository,
                                    ApprovalRepository approvalRepository,
                                    DecisionRecordRepository decisionRecordRepository,
                                    ObjectMapper objectMapper) {
        this.workflowRunRepository = workflowRunRepository;
        this.stageRunRepository = stageRunRepository;
        this.approvalRepository = approvalRepository;
        this.decisionRecordRepository = decisionRecordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRun saveRun(WorkflowRun run) {
        run.setUpdatedAt(Instant.now());
        return workflowRunRepository.save(run);
    }

    @Transactional
    public StageRun saveStage(StageRun stage) {
        return stageRunRepository.save(stage);
    }

    @Transactional
    public Approval saveApproval(Approval approval) {
        return approvalRepository.save(approval);
    }

    @Transactional
    public void lineage(String runId, Actor actor, String action, String rationale, Object payload) {
        DecisionRecord record = new DecisionRecord();
        record.setId(UUID.randomUUID().toString());
        record.setRunId(runId);
        record.setActor(actor);
        record.setAction(action);
        record.setRationale(rationale);
        record.setPayloadJson(writeJson(payload));
        record.setCreatedAt(Instant.now());
        decisionRecordRepository.save(record);
    }

    public Map<String, Object> readContext(WorkflowRun run) {
        return readMap(run.getContextJson());
    }

    public void writeContext(WorkflowRun run, Map<String, Object> context) {
        run.setContextJson(writeJson(context));
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Map<String, Object> readMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new java.util.LinkedHashMap<>();
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
