# Codebase guide

This guide explains how the Agentic URL Shortener is structured and how its main requests move through the code. It is intended as a reading companion to the source, not as a replacement for the API reference or the existing [architecture overview](./ARCHITECTURE.md).

## 1. What the application does

The project is one Spring Boot application with two related capabilities:

1. A URL-shortening service that creates short codes, redirects visitors, records click events, and exposes analytics.
2. A governed SDLC workflow that advances a request through requirement, architecture, implementation, documentation, testing, and release-readiness stages.

The workflow is persisted and reviewable, but its agents are deterministic Java code. They generate structured results and artifacts and exercise the live shortener; they do not call an LLM or edit the repository's source files at runtime.

## 2. Repository map

```text
src/main/java/com/assignment/agentic/
  AgenticUrlShortenerApplication.java  Spring Boot entry point
  config/                              App configuration and cross-cutting web behavior
  shortener/
    api/                               REST endpoints and HTTP errors
    api/dto/                           Request and response shapes
    domain/                            JPA entities for links and clicks
    repo/                              Spring Data repositories
    service/                           Link, safety, analytics, retention, and hashing logic
  orchestrator/
    agent/                             Deterministic stage implementations
    api/                               Workflow REST API
    api/dto/                           Workflow request/response shapes
    definition/                        SDLC stage graph
    domain/                            Persisted run, stage, approval, and decision entities
    engine/                            Workflow scheduling and persistence helpers
    metrics/                           Micrometer reliability metrics
    model/                             Workflow enums and stage/result records
    policy/                            Entry/exit gates and approval rules
    repo/                              Workflow Spring Data repositories
    store/                             Generated artifact writer
src/main/resources/
  application.yml                      Runtime configuration
  db/migration/V1__init.sql            Initial schema, applied by Flyway
  static/index.html                    Browser-based operator console
src/test/java/                         Unit and Spring Boot integration tests
docs/                                  Assignment, design, scenario, and testing guides
```

## 3. How the application starts

[`AgenticUrlShortenerApplication`](../src/main/java/com/assignment/agentic/AgenticUrlShortenerApplication.java) is the entry point. `@SpringBootApplication` enables component scanning and auto-configuration; `@EnableAsync` enables asynchronous click recording; and `@ConfigurationPropertiesScan` binds the `app.*` settings into [`AppProperties`](../src/main/java/com/assignment/agentic/config/AppProperties.java).

On startup, Spring Boot:

1. Loads [`application.yml`](../src/main/resources/application.yml).
2. Runs Flyway migration [`V1__init.sql`](../src/main/resources/db/migration/V1__init.sql).
3. Checks that the JPA entities match the migrated schema (`ddl-auto: validate`).
4. Creates the Spring controllers, services, repositories, filters, executor, and metrics components.

The configured H2 database is file-backed at `./data/shortener`; workflow artifacts are written to `./data/artifacts`. The H2 console is enabled for local development. Actuator exposes health, info, Prometheus, and metrics endpoints.

[`AsyncConfig`](../src/main/java/com/assignment/agentic/config/AsyncConfig.java) defines the bounded thread pool used by orchestration. [`OpenApiConfig`](../src/main/java/com/assignment/agentic/config/OpenApiConfig.java) supplies the Swagger title and description.

## 4. URL shortener

### 4.1 HTTP API

[`ShortUrlController`](../src/main/java/com/assignment/agentic/shortener/api/ShortUrlController.java) exposes the versioned API:

| Method and path | Purpose | Main result |
| --- | --- | --- |
| `POST /api/v1/shorten` | Create a short URL | `201 Created` with a `ShortUrlResponse` |
| `GET /api/v1/urls/{code}` | Read link metadata | Link details and click count |
| `GET /api/v1/urls/{code}/analytics` | Read analytics | Totals, unique visitors, referrers, recent clicks |
| `DELETE /api/v1/urls/{code}` | Deactivate a link | `204 No Content` |

The redirect endpoint is separate: [`RedirectController`](../src/main/java/com/assignment/agentic/shortener/api/RedirectController.java) implements `GET /r/{code}`. It resolves an active link, schedules click recording, and returns a `302 Found` with the original URL in the `Location` header.

DTOs define the API shapes:

- [`CreateShortUrlRequest`](../src/main/java/com/assignment/agentic/shortener/api/dto/CreateShortUrlRequest.java): required URL, optional alias, optional expiration time.
- [`ShortUrlResponse`](../src/main/java/com/assignment/agentic/shortener/api/dto/ShortUrlResponse.java): code, original URL, short path, status, timestamps, and click count.
- [`AnalyticsResponse`](../src/main/java/com/assignment/agentic/shortener/api/dto/AnalyticsResponse.java): aggregate metrics and nested referrer/recent-click entries.

### 4.2 Create-link request flow

```text
HTTP POST
  -> SecurityFilters (headers and shorten rate limit)
  -> ShortUrlController.create
  -> ShortUrlService.create
       -> UrlSafetyValidator.validateAndNormalize
       -> validate expiry
       -> CodeGenerator.validateAlias OR allocate random code
       -> ShortUrlRepository.save
  -> ShortUrlResponse
```

[`ShortUrlService`](../src/main/java/com/assignment/agentic/shortener/service/ShortUrlService.java) owns the business operation and transaction. It validates the URL, rejects an expiration time that is not in the future, generates a random code or checks an alias, and saves the [`ShortUrl`](../src/main/java/com/assignment/agentic/shortener/domain/ShortUrl.java) entity. Random code allocation tries at most eight candidate values before returning a service-unavailable error. The database's unique constraint is the final protection against duplicate codes.

[`CodeGenerator`](../src/main/java/com/assignment/agentic/shortener/service/CodeGenerator.java) uses `SecureRandom` to select seven-character base62 codes by default. Aliases are restricted to 3–32 letters, digits, underscores, or hyphens; `api`, `actuator`, and `swagger-ui` are reserved. Alias conflicts return a conflict error. Invalid alias syntax currently raises `IllegalArgumentException`, which the global handler maps to a `400` response.

[`UrlSafetyValidator`](../src/main/java/com/assignment/agentic/shortener/service/UrlSafetyValidator.java) parses and normalizes the URL, enforces the configured maximum length, accepts only `http` and `https`, requires a host, blocks known local/internal names, and rejects DNS results that resolve to local, private, link-local, or multicast addresses.

### 4.3 Redirect and click recording

[`RedirectController`](../src/main/java/com/assignment/agentic/shortener/api/RedirectController.java) asks `ShortUrlService.resolveActive` to find the code and reject missing, inactive, or expired links. If valid, it invokes `recordClick` and redirects immediately.

`ShortUrlService.recordClick` is annotated `@Async`. It stores a [`ClickEvent`](../src/main/java/com/assignment/agentic/shortener/domain/ClickEvent.java) containing the link ID, click time, HMAC-hashed IP, user agent, and referrer. The raw IP is not stored in the event. [`IpHasher`](../src/main/java/com/assignment/agentic/shortener/service/IpHasher.java) uses the configured HMAC-SHA256 secret.

This is best-effort analytics: the redirect does not wait for the event write, so a process failure immediately after the redirect may lose that click.

### 4.4 Metadata and analytics

`ShortUrlService.get` returns metadata and counts events. [`AnalyticsService`](../src/main/java/com/assignment/agentic/shortener/service/AnalyticsService.java) calculates:

- Total clicks and clicks in the last 24 hours.
- Unique visitors, counted as distinct non-null IP hashes across all events for the link.
- Up to five most-frequent referrers; missing/blank referrers are labelled `(direct)`.
- Up to 100 recent clicks.

The current implementation loads all click events for the link to calculate unique visitors and referrer counts. This is straightforward for a prototype but may use substantial memory for high-volume links; a production-scale version would generally aggregate in the database or a separate analytics store.

### 4.5 Expiration and deactivation

`ShortUrlService.deactivate` sets `active=false`. `resolveActive` checks both this flag and the expiration timestamp, returning `410 Gone` for inactive or expired links.

[`RetentionService`](../src/main/java/com/assignment/agentic/shortener/service/RetentionService.java) provides `deactivateExpired()`, which finds active links whose expiration time has passed and deactivates them. The repository includes the query for this operation. It is invoked by the orchestrator's smoke-test stage; this project does not configure a scheduled periodic sweep.

### 4.6 Persistence model

The shortener stores data in two tables:

- `short_urls`: one row per link, with unique code, target URL, alias flag, active flag, creator, and timestamps.
- `click_events`: one row per recorded click, linked to a short URL ID.

[`ShortUrlRepository`](../src/main/java/com/assignment/agentic/shortener/repo/ShortUrlRepository.java) and [`ClickEventRepository`](../src/main/java/com/assignment/agentic/shortener/repo/ClickEventRepository.java) are Spring Data interfaces. Their method names express common lookups and counts; Spring Data derives the SQL implementations.

## 5. Cross-cutting HTTP behavior

[`SecurityFilters`](../src/main/java/com/assignment/agentic/config/SecurityFilters.java) contains two servlet filters:

- `SecurityHeadersFilter` adds `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and `Cache-Control` headers.
- `RateLimitFilter` limits `POST /api/v1/shorten` to the configured number per minute per client-IP key. It uses an in-memory, fixed-minute counter, so its state is lost on restart and is not shared between application instances.

Both filters accept the first `X-Forwarded-For` value when present. This assumes that only a trusted proxy can set that header; a deployment exposed directly to clients should configure trusted-proxy handling rather than relying on arbitrary forwarded headers.

[`GlobalExceptionHandler`](../src/main/java/com/assignment/agentic/config/GlobalExceptionHandler.java) maps `ApiException`, bean-validation failures, and `IllegalArgumentException` to JSON error responses. [`ApiException`](../src/main/java/com/assignment/agentic/shortener/api/ApiException.java) carries an HTTP status and stable error code.

## 6. Orchestrator

### 6.1 Public API and operator console

[`OrchestratorController`](../src/main/java/com/assignment/agentic/orchestrator/api/OrchestratorController.java) exposes:

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/orchestrator/runs` | Start a workflow run |
| `GET /api/v1/orchestrator/runs` | List runs, newest first |
| `GET /api/v1/orchestrator/runs/{id}` | Read context, stage states, approvals, and lineage |
| `POST /api/v1/orchestrator/runs/{id}/approvals/{approvalId}` | Approve or reject a checkpoint |
| `POST /api/v1/orchestrator/runs/{id}/stop` | Request a cooperative safe stop |
| `POST /api/v1/orchestrator/runs/{id}/replan` | Replace the requirement and invalidate downstream stages |
| `GET /api/v1/orchestrator/metrics` | Read workflow reliability counters |
| `GET /api/v1/orchestrator/definition` | Inspect the stage graph and join description |
| `GET /api/v1/orchestrator/scenarios` | List the three supported scenarios |

The request and response records are grouped in [`OrchestratorDtos`](../src/main/java/com/assignment/agentic/orchestrator/api/dto/OrchestratorDtos.java). Missing scenario and autonomy values default to `GREENFIELD` and `SUPERVISED`; an absent requirement uses scenario-specific example text.

[`index.html`](../src/main/resources/static/index.html) is a small browser console served by Spring Boot at `/`. It starts scenarios, lists and refreshes runs, displays status and lineage, and lets an operator approve, reject, stop, or replan. It calls the same orchestrator HTTP endpoints as other clients.

### 6.2 Workflow graph and model

[`SdlcDefinitions`](../src/main/java/com/assignment/agentic/orchestrator/definition/SdlcDefinitions.java) defines workflow `sdlc-v1`:

```text
requirements
  -> architecture
       -> implementation ---\
       -> documentation -----+-> testing -> release_readiness
```

Each [`StageNode`](../src/main/java/com/assignment/agentic/orchestrator/model/OrchestrationModels.java) names a stage, its dependencies, and metadata such as parallel safety and impact. `WorkflowEngine.isReady` permits a pending stage to run only when every dependency is `SUCCEEDED`.

The same model file defines:

- `Scenario`: `GREENFIELD`, `BROWNFIELD`, or `AMBIGUOUS`.
- `AutonomyMode`: `FULL_AUTO`, `SUPERVISED`, or `MANUAL`.
- Run, stage, approval, and actor enums.
- `AgentResult`, the common output envelope: success, clarification/approval flags, summary, structured output, artifact paths, risks, and fallback label.

The documentation graph declares implementation and documentation as parallel-safe branches. In the current engine, however, `executeStage` synchronizes on a per-run lock while calling the stage agent. As a result, stages in the same run do not execute their agent bodies concurrently, even though the scheduler submits ready stages as executor tasks. The AND dependency is enforced; actual parallel execution is not achieved.

### 6.3 What each stage agent does

[`StageAgents`](../src/main/java/com/assignment/agentic/orchestrator/agent/StageAgents.java) dispatches by stage ID:

1. **Requirements:** Reads the raw request, creates normalized functional/non-functional requirements and assumptions, and lists ambiguities for the ambiguous scenario. Without accepted defaults, it reports that a human clarification is needed.
2. **Architecture:** Produces a design summary and, for brownfield, names impacted modules.
3. **Implementation:** Calls the existing `ShortUrlService` to create a demonstration link, then writes an evidence artifact. It does not modify Java source files.
4. **Documentation:** Writes a short runbook artifact for the scenario.
5. **Testing:** Runs in-process smoke checks against the live service: create/resolve, analytics access, loopback URL rejection, non-HTTP rejection, and the expiry sweep. It does not launch `mvn test`.
6. **Release readiness:** Creates a release checklist with test status, controls, rollback notes, and residual risks. It returns a result that causes the workflow to require human release approval.

Artifacts are written under `./data/artifacts/{runId}/{stageId}/`. [`ArtifactStore`](../src/main/java/com/assignment/agentic/orchestrator/store/ArtifactStore.java) creates directories and writes UTF-8 files with a generated-at header. Generated artifacts are local filesystem files, while stage input/output and lineage are stored in the database.

### 6.4 Policy gates and approval behavior

[`PolicyEngine`](../src/main/java/com/assignment/agentic/orchestrator/policy/PolicyEngine.java) evaluates:

- **Entry gates:** safe-stop state, architecture prerequisite for implementation, and test prerequisite for release.
- **Exit gates:** non-empty stage output, plus `passed=true` for testing.
- **Approval policy:** every stage in `MANUAL`; architecture in `SUPERVISED`; brownfield implementation outside `FULL_AUTO`; release in every mode; and any stage explicitly marked for forced approval in context.
- **Clarification:** ambiguous requirements that have not accepted assumptions pause before architecture proceeds.

An approval is stored with a reason and status. `WorkflowEngine.decide` validates that the approval belongs to the run and is still pending, persists the human decision and comment, and schedules the workflow again. A rejection marks the run failed. Release always needs a human approval.

### 6.5 Run lifecycle in `WorkflowEngine`

[`WorkflowEngine`](../src/main/java/com/assignment/agentic/orchestrator/engine/WorkflowEngine.java) coordinates the workflow:

1. **Start:** Create a `WorkflowRun`, initialize context, create one pending `StageRun` per graph node, record a `START` lineage event, increment metrics, and submit a pump task.
2. **Schedule:** Find pending stages whose dependencies succeeded. If one is waiting for approval, expose a waiting run status. If every stage succeeded, complete the run.
3. **Execute:** Evaluate the entry gate; pause for approval if required; record stage input; invoke the matching stage agent; evaluate the exit gate; store output, context, and lineage.
4. **Retry:** On an unsuccessful result or exception, retry up to `app.orchestrator.max-retries`, using an increasing backoff based on the attempt number.
5. **Rollback/fail:** After retries are exhausted, remove that stage's context, mark the stage rolled back, invalidate direct dependent stages, record the rollback, and fail the run.
6. **Stop:** A stop request is cooperative. The scheduler observes it at a gate, marks the run stopped, records lineage, and exits. It does not forcibly interrupt an agent midway through an operation.
7. **Replan:** Replace the raw requirement, increment context version, reset requirements, mark later stages invalidated, clear terminal/error fields, record lineage, and resume scheduling.

The scheduler uses an in-memory per-run lock map for coordination. The H2 database holds run state across process restarts, but the pump itself is in-process: there is no distributed scheduler that resumes running/pending jobs after a process restart.

### 6.6 Persistence and audit lineage

[`WorkflowRun`](../src/main/java/com/assignment/agentic/orchestrator/domain/WorkflowRun.java) stores the scenario, autonomy mode, current status, context JSON, stop flag, context version, timestamps, and error. [`StageRun`](../src/main/java/com/assignment/agentic/orchestrator/domain/StageRun.java) stores each stage's status, attempts, input/output JSON, error, and timestamps. [`Approval`](../src/main/java/com/assignment/agentic/orchestrator/domain/Approval.java) stores each human checkpoint. [`DecisionRecord`](../src/main/java/com/assignment/agentic/orchestrator/domain/DecisionRecord.java) stores actor, action, rationale, payload, and time.

[`OrchestrationPersistence`](../src/main/java/com/assignment/agentic/orchestrator/engine/OrchestrationPersistence.java) wraps repository writes, updates run timestamps, and serializes workflow context and lineage payloads with Jackson. Individual Spring Data repository interfaces provide persistence operations for each entity.

The initial database schema in [`V1__init.sql`](../src/main/resources/db/migration/V1__init.sql) defines the `workflow_runs`, `stage_runs`, `approvals`, and `decision_records` tables alongside the shortener tables. Foreign keys tie stages, approvals, and decisions to their workflow run.

### 6.7 Metrics

[`OrchestrationMetrics`](../src/main/java/com/assignment/agentic/orchestrator/metrics/OrchestrationMetrics.java) registers Micrometer metrics for successes, failures, retries, rollbacks, latency, and in-flight runs. The orchestrator metrics API exposes success rate, retry count, rollback count, and mean latency.

These counters are in the application's active Micrometer registry and are not a durable per-run historical metrics store. In this prototype, in-flight metric updates and workflow pumping happen in-process.

## 7. Configuration reference

[`application.yml`](../src/main/resources/application.yml) contains the active defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | H2 file database in PostgreSQL compatibility mode | Local persistence |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Check entities against Flyway schema; do not create schema |
| `app.shortener.code-length` | `7` | Generated-code length |
| `app.shortener.max-url-length` | `2048` | Maximum input URL length |
| `app.shortener.rate-limit-per-minute` | `30` | Shorten requests per minute per IP key |
| `app.shortener.hmac-secret` | Local-development placeholder | HMAC key for visitor IP hashes |
| `app.orchestrator.max-retries` | `3` | Maximum stage attempts |
| `app.orchestrator.retry-backoff-ms` | `80` | Base retry delay, multiplied by attempt |
| `app.orchestrator.artifact-dir` | `./data/artifacts` | Root folder for workflow artifacts |

The HMAC value shown in the repository is a development default, not a production secret. Use a securely managed deployment-specific secret outside source control.

## 8. Tests and what they demonstrate

Tests live under `src/test/java`:

- [`UrlSafetyValidatorTest`](../src/test/java/com/assignment/agentic/shortener/UrlSafetyValidatorTest.java) checks acceptance of a public HTTPS URL and rejection of loopback and non-HTTP schemes.
- [`ShortUrlApiIT`](../src/test/java/com/assignment/agentic/shortener/ShortUrlApiIT.java) starts the Spring application and exercises create/redirect/metadata, duplicate aliases, and private-target rejection.
- [`PolicyEngineTest`](../src/test/java/com/assignment/agentic/orchestrator/PolicyEngineTest.java) checks release approval, testing exit requirements, and supervised architecture approval.
- [`WorkflowEngineIT`](../src/test/java/com/assignment/agentic/orchestrator/WorkflowEngineIT.java) exercises greenfield completion with release approval, ambiguous clarification, and the exposed DAG join description.

Run the suite with:

```bash
mvn test
```

The workflow integration test's “full auto” run still pauses for release approval by design. Test coverage does not currently prove true concurrent stage execution or fully exercise stop, replan, retry-exhaustion, rollback, and rejected-approval behavior.

## 9. Important behavior notes and prototype boundaries

- The response DTO's `shortPath` is currently constructed as `/{code}`, while the actual redirect controller is mounted at `/r/{code}`. Use `/r/{code}` as the working redirect URL; this response mismatch is a code-level inconsistency.
- Hostname safety checks resolve DNS when validating a submitted URL. DNS rebinding after validation remains possible; the validator also treats a resolution exception as not-private, so it is not a complete production SSRF boundary.
- The rate limiter is per-process and in-memory, and forwarded IP headers must only be trusted behind a controlled proxy.
- Click recording is asynchronous and best-effort. Analytics currently aggregates in application memory from event rows.
- A link expiration check happens during redirect even if the retention sweep has not yet run.
- The workflow's DAG, persisted state, approvals, gates, retries, lineage, and artifacts are real application behavior. The stage “agents” are deterministic Java implementations, not general-purpose AI agents.
- “Rollback” compensates workflow state and invalidates dependents; it does not reverse external side effects such as a short URL created by an implementation demonstration.
- The prototype uses one application instance and H2 file storage. It does not provide cluster-wide locking, a distributed scheduler, production authentication/authorization, or multi-node rate limiting.

For the documented assignment trade-offs and scenario scripts, see [TESTING.md](./TESTING.md), [SCENARIOS.md](./SCENARIOS.md), and [ENGINEERING_SUMMARY.md](./ENGINEERING_SUMMARY.md).
