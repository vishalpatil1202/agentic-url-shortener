# Testing approach, limitations, and trade-offs

## Approach

| Layer | What |
| --- | --- |
| Unit | `UrlSafetyValidator`, `PolicyEngine` (approval and exit gates) |
| API | `ShortUrlApiIT` — create, redirect, alias conflict, SSRF reject |
| Orchestration | `WorkflowEngineIT` — greenfield full-auto → release approval → success; ambiguous clarification; DAG join semantics |

Run: `mvn test`

The testing **agent** is also an in-process harness used during workflow runs. It is intentionally not `mvn test` recursively (that would deadlock a test that starts the engine).

## Reliability features exercised

- Unique code allocation with retry
- Custom alias 409
- Expired/inactive → 410 on redirect
- Rate limit on `POST /api/v1/shorten`
- Private/loopback/non-http rejection
- Async click capture (best-effort; redirect does not wait)

## Limitations

- Single node, file H2 — not multi-AZ, not horizontally scaled.
- In-memory rate limiter and in-process orchestrator pump do not cluster.
- Code allocation uniqueness is check-then-insert with retry, not a globally sequential generator.
- Hostname SSRF uses `InetAddress.getByName` (DNS at validation time); DNS rebinding after validation is residual risk.
- No end-user authentication on the shortener; operator identity on approvals is a string, not SSO.
- Stage agents do not call an LLM and do not rewrite source files at runtime. They produce artifacts and execute the live module.
- MTTR is approximated by run latency on failure, not a paging/on-call system.

## Trade-offs

| Choice | Why | Cost |
| --- | --- | --- |
| Modular monolith | One runnable prototype, clear module boundaries | Not independently deployable |
| Deterministic agents | Auditable, testable, interview-defensible | Less “magical” than an LLM planner |
| `/r/{code}` | No route collisions | Slightly less pretty than `/{code}` |
| File H2 | Demo in minutes | Not production durability |
| Human release gate always on | Matches “controlled autonomy” | Cannot fully automate prod push |
| Async clicks | Fast redirect | Click may be lost if process dies immediately after 302 |

These are deliberate prototype boundaries, not accidental omissions.
