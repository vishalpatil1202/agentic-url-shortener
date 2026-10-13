# Three scenarios

Each scenario uses the same DAG. Decomposition, orchestration, and validation change with the problem, not the engine.

## 1. Greenfield — new system

**Raw intent:** build a URL shortener with create, redirect, analytics, and reliability.

**Decomposition**

1. Normalize functional vs non-functional requirements.
2. Choose modular monolith, public redirect path, privacy-preserving analytics.
3. Implement core APIs (already in this repo; the implementation agent captures live evidence).
4. Document the operator runbook in parallel with implementation.
5. Run SSRF, scheme, create/resolve, and retention checks.
6. Gate release on a human.

**Orchestration:** `SUPERVISED` pauses on architecture and release. `FULL_AUTO` pauses only on release.

**Validation:** testing agent must pass the exit gate (`passed=true`) or the join into release never opens.

Try:

```json
{ "scenario": "GREENFIELD", "autonomyMode": "SUPERVISED" }
```

## 2. Brownfield — enhance a live module

**Raw intent:** add custom aliases, expiry, and a retention sweeper without breaking existing random codes.

**Impacted modules (codebase reasoning)**

- `CodeGenerator.validateAlias` — charset, length, reserved words
- `ShortUrlService.create` — uniqueness / 409 on alias clash
- `RetentionService.deactivateExpired`
- `AnalyticsService` — still correct after expiry

**Orchestration:** implementation is high-impact in brownfield + `SUPERVISED`, so change-control approval is required before the agent mutates/demonstrates the live module. Documentation still runs in parallel; testing AND-joins both.

**Validation:** conflict on duplicate alias, SSRF still blocked, retention sweep executes.

Try:

```json
{ "scenario": "BROWNFIELD", "autonomyMode": "SUPERVISED" }
```

## 3. Ambiguous — underspecified request

**Raw intent:** “Make something like bitly but enterprise-ready.”

**Decomposition:** the requirements agent refuses to pretend this is complete. It lists ambiguities (SLA, aliases, retention, auth) and either:

- pauses for **human clarification**, or
- applies an explicit **assumption set** when `acceptDefaultAssumptions=true` / the clarification is approved.

**Orchestration:** `WAITING_CLARIFICATION` is a first-class run status. Downstream architecture does not start until assumptions are accepted. If the operator later changes the requirement, `POST .../replan` invalidates downstream stages and reschedules — non-linear execution with preserved lineage.

**Validation:** exit gate on requirements allows a clarification pause; it does not allow silent invention of SLAs.

Try:

```json
{ "scenario": "AMBIGUOUS", "autonomyMode": "FULL_AUTO", "acceptDefaultAssumptions": false }
```

Approve the clarification, then approve release.
