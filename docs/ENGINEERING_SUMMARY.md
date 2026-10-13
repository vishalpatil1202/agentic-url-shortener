# Final engineering summary

## Plan and rationale

The assignment asks for two outcomes that are easy to fake separately and hard to do together: a **credible engineering product** (URL shortener) and a **credible agentic control plane** (SDLC orchestration with governance).

The plan:

1. Treat the shortener as the system of record for “engineering output quality.”
2. Treat the orchestrator as a stateful DAG runtime with policy, not a prompt chain.
3. Bind them: implementation and testing stages call the live shortener, so a green workflow is evidence, not theater.
4. Keep humans in the loop for architecture (supervised), brownfield mutation, clarification, and every release.

## Artifacts

- Runnable Spring Boot 3 / Java 17 service
- Flyway schema for short URLs, clicks, workflow runs, stages, approvals, lineage
- Operator console at `/`
- Generated per-run files under `./data/artifacts/{runId}/{stage}/`
- OpenAPI at `/swagger-ui.html`
- Tests under `src/test/java`

## Risks, trade-offs, validation

See [TESTING.md](TESTING.md). Highest residual risks: single-node data store, DNS-rebinding SSRF window, unauthenticated shorten API, best-effort analytics writes.

Validation strategy: unit gates + HTTP tests + an orchestration IT that will not go `SUCCEEDED` unless the testing exit gate passed and a human approved release.

## Assumptions

- Interview environment is local JDK 17 + Maven; no Kubernetes required.
- “Enterprise-ready” in the ambiguous prompt means security/privacy/change-control **posture**, not a full HA build in 2–3 days.
- Operator approvals are the stand-in for change-management CAB.
- Custom aliases are optional; random codes remain the default.

## Limitations

- Prototype autonomy: agents cannot patch Git or deploy to a real environment.
- Observability is process-local Prometheus + table-backed lineage, not a SIEM.
- No multi-tenant isolation or API keys.

## How this maps to the rubric

| Criterion | Where it shows up |
| --- | --- |
| Agentic orchestration | `WorkflowEngine` DAG, AND-join, replan, lineage |
| Architecture quality | Bounded contexts, redirect isolation, privacy hashing |
| Decomposition | Scenario docs + requirements agent output |
| Realism of outputs | Working APIs, artifacts, live validation |
| Validation and risk | Policy gates, SSRF tests, explicit limitations |
| Controlled autonomy | `AutonomyMode` + mandatory release approval |
| Engineering judgment | `/r/{code}`, no LLM required for correctness, H2 for demo friction |
