package com.assignment.agentic.orchestrator.definition;

import com.assignment.agentic.orchestrator.model.OrchestrationModels.StageNode;
import com.assignment.agentic.orchestrator.model.OrchestrationModels.WorkflowDefinition;

import java.util.List;
import java.util.Set;

public final class SdlcDefinitions {
    public static final String REQUIREMENTS = "requirements";
    public static final String ARCHITECTURE = "architecture";
    public static final String IMPLEMENTATION = "implementation";
    public static final String DOCUMENTATION = "documentation";
    public static final String TESTING = "testing";
    public static final String RELEASE = "release_readiness";

    private SdlcDefinitions() {
    }

    /**
     * Non-linear SDLC DAG:
     * requirements -> architecture -> (implementation || documentation) -> testing -> release
     * Testing joins both parallel branches (AND synchronization).
     */
    public static WorkflowDefinition sdlc() {
        return new WorkflowDefinition(
                "sdlc-v1",
                "Governed SDLC lifecycle",
                List.of(
                        new StageNode(REQUIREMENTS, "Requirement understanding", Set.of(), true, false, true),
                        new StageNode(ARCHITECTURE, "Architecture and design", Set.of(REQUIREMENTS), false, true, false),
                        new StageNode(IMPLEMENTATION, "Implementation", Set.of(ARCHITECTURE), true, true, false),
                        new StageNode(DOCUMENTATION, "Documentation", Set.of(ARCHITECTURE), true, false, false),
                        new StageNode(TESTING, "Validation and testing", Set.of(IMPLEMENTATION, DOCUMENTATION), false, false, false),
                        new StageNode(RELEASE, "Release readiness", Set.of(TESTING), false, true, false)
                )
        );
    }
}
