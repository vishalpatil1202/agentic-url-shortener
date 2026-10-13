# Agentic URL Shortener

Interview prototype that pairs a production-minded URL shortener with a **governed, stateful SDLC orchestrator**. Agents execute multi-step engineering work; humans own high-impact approvals and final quality.

## What you get

1. **URL shortener** — create, redirect, metadata, analytics, deactivate, expiry/retention, SSRF guards, rate limits.
2. **Agentic orchestrator** — explicit SDLC DAG, entry/exit gates, parallel stages with AND-join, decision lineage, bounded retries, rollback, safe-stop, replan, policy guardrails, and reliability metrics.
3. **Three scenarios** — greenfield, brownfield, and ambiguous requirements.

## Prerequisites

- JDK 17
- Maven 3.9+

## Run

```bash
mvn test
mvn spring-boot:run
```

Then open:

| Surface | URL |
| --- | --- |
| Operator console | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| H2 console | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/shortener`) |

Data and generated artifacts land in `./data`.

## Quick demo

**Shorten and resolve**

```bash
curl -s -X POST http://localhost:8080/api/v1/shorten ^
  -H "Content-Type: application/json" ^
  -d "{\"url\":\"https://example.com/docs\"}"
```

Use the returned `code`:

```bash
curl -I http://localhost:8080/r/{code}
curl http://localhost:8080/api/v1/urls/{code}/analytics
```

**Run a governed SDLC scenario**

```bash
curl -s -X POST http://localhost:8080/api/v1/orchestrator/runs ^
  -H "Content-Type: application/json" ^
  -d "{\"scenario\":\"GREENFIELD\",\"autonomyMode\":\"SUPERVISED\"}"
```

Poll `GET /api/v1/orchestrator/runs/{id}` until status is `WAITING_APPROVAL`, then approve from the console or:

```bash
curl -s -X POST http://localhost:8080/api/v1/orchestrator/runs/{id}/approvals/{approvalId} ^
  -H "Content-Type: application/json" ^
  -d "{\"approved\":true,\"actor\":\"reviewer\",\"comment\":\"design ok\"}"
```

Release always requires a human, even in `FULL_AUTO`.

## Docs

- [Architecture](docs/ARCHITECTURE.md)
- [Scenarios](docs/SCENARIOS.md)
- [Testing, limitations, trade-offs](docs/TESTING.md)
- [Engineering summary](docs/ENGINEERING_SUMMARY.md)

## API sketch

**Shortener**

- `POST /api/v1/shorten` `{ "url", "alias?", "expiresAt?" }`
- `GET /r/{code}` 302 redirect + async click capture
- `GET /api/v1/urls/{code}`
- `GET /api/v1/urls/{code}/analytics`
- `DELETE /api/v1/urls/{code}`

**Orchestrator**

- `POST /api/v1/orchestrator/runs`
- `GET /api/v1/orchestrator/runs/{id}`
- `POST /api/v1/orchestrator/runs/{id}/approvals/{approvalId}`
- `POST /api/v1/orchestrator/runs/{id}/stop`
- `POST /api/v1/orchestrator/runs/{id}/replan`
- `GET /api/v1/orchestrator/metrics`
- `GET /api/v1/orchestrator/definition`
