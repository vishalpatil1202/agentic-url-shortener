# Architecture overview

## Components

```
┌──────────────────────────────────────────────────────────┐
│                     Spring Boot app                      │
│                                                          │
│  ┌─────────────────────┐   ┌──────────────────────────┐  │
│  │ Shortener context   │   │ Orchestrator context     │  │
│  │  API, domain,       │◄──│  DAG engine, policy,     │  │
│  │  analytics,         │   │  agents, approvals,      │  │
│  │  retention, safety  │   │  lineage, metrics        │  │
│  └─────────┬───────────┘   └────────────┬─────────────┘  │
│            │                            │                │
│            └────────── H2 + Flyway ─────┘                │
│                         ArtifactStore (./data/artifacts) │
└──────────────────────────────────────────────────────────┘
```

Two bounded contexts in a **modular monolith**. That keeps the prototype runnable with one command while preserving a clean split if the orchestrator later becomes its own service.

## URL shortener

| Concern | Choice |
| --- | --- |
| Identity | Cryptographically random 7-char base62 codes; optional custom alias |
| Redirect | `GET /r/{code}` so codes cannot collide with `/api` or `/actuator` |
| Analytics | Click events with HMAC-hashed IP, UA, referrer; aggregates + recent sample |
| Safety | http(s) only; block loopback/private/link-local/multicast; reserved aliases |
| Reliability | Unique code retry, rate-limit on shorten, async click write, expiry sweeper |
| Privacy | Raw client IPs are never stored |

## Orchestration model

The engine is **not** a linear script. It is a persisted state machine over an explicit DAG.

```
requirements
     │  entry/exit gates, optional clarification checkpoint
architecture          ← high-impact; approval in SUPERVISED/MANUAL
     │
     ├──────────────┐
implementation   documentation     ← parallel, then AND-join
     └──────────────┘
            │
         testing     ← exit gate requires passed=true
            │
    release_readiness  ← always human-approved
```

### Control flow

1. **Entry gate** — policy (security/change-control) + optional human approval.
2. **Execute agent** with bounded retries and backoff.
3. **Exit gate** — artifacts/output present; testing must pass.
4. **Persist context + decision lineage** after every transition.
5. **AND-join** — `testing` is ready only when *both* `implementation` and `documentation` succeeded.
6. **Safe-stop** — cooperative halt at the next gate; in-flight attempt is not killed mid-write.
7. **Rollback** — drop the failed stage's context, invalidate dependents, fail the run.
8. **Replan** — bump `contextVersion`, invalidate downstream stages, reschedule from requirements.

Autonomy is bounded:

| Mode | Agent may run without asking | Human must approve |
| --- | --- | --- |
| `FULL_AUTO` | All stages except release | Release (and clarifications) |
| `SUPERVISED` | Requirements, docs, tests | Architecture, brownfield implementation, release |
| `MANUAL` | Nothing | Every stage |

## Key decisions

1. **Governed agents, not a chatbot.** Stage agents are deterministic specialists that write artifacts and, in implementation/testing, exercise the live shortener module. That is more defensible in an interview than wrapping a generic LLM.
2. **State is in the database.** Runs survive process restart for inspection; the pump is in-process for the prototype.
3. **Redirect prefix `/r/`.** Collision-free routing beats the prettier `/{code}` UX for a service that also hosts APIs.
4. **Validation is in-process.** The testing agent calls the same Spring beans the HTTP API uses. That proves the module works without shelling out to `mvn test` from inside a test.
5. **H2 file database.** Zero-ops local run. SQL is PostgreSQL-compatible so promotion is a datasource change, not a rewrite.

## Observability

- Actuator health + Prometheus metrics
- Orchestrator counters: success, failure, retries, rollbacks, in-flight, latency
- Append-only `decision_records` (actor, action, rationale, payload)
- Stage input/output JSON on each `stage_runs` row
