# backend

Spring Boot harness for the Software Analyst Workflow Assistant. It routes skill requests, enforces profile permissions, persists reviewable artifacts, and gates downstream handoffs behind human review.

See [../docs/architecture.md](../docs/architecture.md) for the full design.

## Main Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Backend health check |
| `POST /api/integrations/jira/import` | Dry-run Jira-like ticket import for Ticket Intake |
| `POST /api/skills/requirement-analysis` | Analyze a requirement or ticket intake payload |
| `POST /api/artifacts/{taskId}/clarify` | Add clarification and create a linked requirement artifact |
| `PATCH /api/artifacts/{taskId}/review` | Mark an artifact as reviewed |
| `POST /api/artifacts/{taskId}/handoff/impact-analysis` | Run impact analysis from a reviewed requirement artifact |
| `POST /api/artifacts/{taskId}/handoff/test-case-gen` | Generate tests from a reviewed impact artifact module |
| `POST /api/artifacts/{taskId}/test-scope` | Save analyst-reviewed testing scope from a generated test-case artifact |
| `POST /api/artifacts/{taskId}/handoff/timeline-estimation` | Estimate timeline from a reviewed impact artifact |
| `POST /api/artifacts/{taskId}/handoff/handoff-summary` | Persist a reviewable handoff summary from requirement, impact, and test artifacts |
| `GET /api/artifacts` | List persisted artifacts |
| `GET /api/artifacts/{taskId}` | Reopen one artifact |

Legacy direct skill endpoints still exist for free-form use:

- `POST /api/skills/code-qa`
- `POST /api/skills/impact-analysis`
- `POST /api/skills/impact-analysis/from-pr`
- `POST /api/skills/test-case-gen`

Jira import is currently a dry-run sample importer. It does not call Jira yet; real Jira read-only import is the next integration phase.

Requirement analysis is rule-based by default for stable local demos. The same `RequirementAnalysisSynthesizer` boundary can be switched to an LLM-backed implementation without changing the controller, coordinator, artifact model, or frontend workflow.

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

Generated test-case artifacts can be turned into linked `test-scope-review` artifacts. This keeps the AI-generated draft and analyst-reviewed testing scope separate in history, while handoff summary prefers reviewed managed scopes when available.

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
  -d "{\"profile\":\"software-analyst\",\"ticket_key\":\"PAY-102\",\"ticket_title\":\"Allow payment method update\",\"priority\":\"High\",\"description\":\"The customer must be able to change payment_method after checkout is submitted.\",\"acceptance_criteria\":\"Customer can update payment method before payment confirmation.\"}"
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

Project-context retrieval defaults to the cloned MyBanjirCare sample project:

```powershell
$env:ANALYSIS_TARGET_PROJECT_NAME="MyBanjirCare"
$env:ANALYSIS_TARGET_PROJECT_PATH="C:\tmp\MyBanjirCare"
```

Change those values to point impact analysis at another local repository.

Impact analysis uses a hybrid context strategy:

1. Query `codebase-memory-mcp` through the local Python MCP server for the configured project.
2. Supplement those matches with local repository file evidence.
3. Fall back to fixed demo context only if neither source returns useful evidence.

Optional LLM-backed requirement analysis:

```powershell
$env:ANALYSIS_REQUIREMENT_PROVIDER="llm"
$env:ANALYSIS_LLM_PROVIDER="openai"
$env:OPENAI_API_KEY="your-local-key"
$env:OPENAI_MODEL="gpt-5-mini"
```

If the provider is enabled but no AI response is available, the backend falls back to rule-based requirement analysis so the workflow remains usable.
