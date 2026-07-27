# backend

Spring Boot harness for the Software Analyst Workflow Assistant. It routes skill requests, enforces profile permissions, persists reviewable artifacts, and gates downstream handoffs behind human review.

See [../docs/architecture.md](../docs/architecture.md) for the full design.

## Main Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Backend health check |
| `POST /api/skills/requirement-analysis` | Analyze a requirement or ticket |
| `POST /api/artifacts/{taskId}/clarify` | Add clarification and create a linked requirement artifact |
| `PATCH /api/artifacts/{taskId}/review` | Mark an artifact as reviewed |
| `POST /api/artifacts/{taskId}/handoff/impact-analysis` | Run impact analysis from a reviewed requirement artifact |
| `POST /api/artifacts/{taskId}/handoff/test-case-gen` | Generate tests from a reviewed impact artifact module |
| `POST /api/artifacts/{taskId}/handoff/timeline-estimation` | Estimate timeline from a reviewed impact artifact |
| `POST /api/artifacts/{taskId}/handoff/handoff-summary` | Persist a reviewable handoff summary from requirement, impact, and test artifacts |
| `GET /api/artifacts` | List persisted artifacts |
| `GET /api/artifacts/{taskId}` | Reopen one artifact |

Legacy direct skill endpoints still exist for free-form use:

- `POST /api/skills/code-qa`
- `POST /api/skills/impact-analysis`
- `POST /api/skills/impact-analysis/from-pr`
- `POST /api/skills/test-case-gen`

## Agent Layer

`backend/src/main/java/com/miniproject/backend/agent/` contains the `Agent` interface and profile implementations. The primary profile is `SoftwareAnalystAgent`, which allows the full workflow skill set.

`AgentRegistry` collects every Spring `Agent` bean automatically. `CoordinatorService` uses it as a permission boundary; the REST route still determines which skill runs.

## Artifact Model

Every skill result is wrapped in an `artifact.v1` envelope and persisted immediately with:

- result JSON
- evidence
- input text
- review state
- parent artifact lineage

Downstream handoffs use reviewed artifacts as their source of truth.

## External Write-Back

Jira and Bitbucket handoff support remains behind `POST /api/artifacts/{taskId}/external-handoff` and `GET /api/artifacts/{taskId}/external-handoffs`. Calls are dry-run by default unless `dry_run: false` is explicitly sent and credentials are configured locally.

## Prerequisites

`mcp-server`'s virtualenv must exist first. See [../mcp-server/README.md](../mcp-server/README.md).

## Run

```powershell
mvn spring-boot:run
```

Backend starts on `http://localhost:8080`.

## Try It

```powershell
curl -X POST http://localhost:8080/api/skills/requirement-analysis `
  -H "Content-Type: application/json" `
  -d "{\"profile\":\"software-analyst\",\"description\":\"The customer must be able to change payment_method after checkout is submitted.\"}"
```

## Test

```powershell
mvn test
```

Some local environments may time out on `McpToolClientIntegrationTest` because it starts the real Python MCP subprocess. For focused workflow checks:

```powershell
mvn -q "-Dtest=SoftwareAnalystAgentTest,AnalysisStatusTest,RequirementAnalysisSkillTest" test
```

## Config

Tracked defaults live in `src/main/resources/application.yml`. Real API keys or integration credentials belong only in `src/main/resources/application-local.yml` or environment variables.
