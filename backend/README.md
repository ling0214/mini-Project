# backend

Spring Boot harness for the Software Analyst Workflow Assistant. It routes skill requests, enforces profile permissions, persists reviewable artifacts, and gates downstream handoffs behind human review.

See [../docs/architecture.md](../docs/architecture.md) for the full design.

## Main Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Backend health check |
| `POST /api/integrations/jira/import` | Read-only Jira issue import for Analyst Inbox, with dry-run fallback when Jira is not configured |
| `POST /api/skills/requirement-analysis` | Analyze a requirement or connector inbox ticket payload |
| `POST /api/artifacts/{taskId}/clarify` | Add structured clarification answers and create a linked requirement artifact |
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

Jira import is read-only. When `integrations.jira.enabled=true` and Jira credentials are configured, it fetches an existing issue by key or URL and maps summary, description, priority, reporter, comments, source URL, and source timestamp into the platform's ticket shape. When Jira is not configured, it keeps the dry-run sample importer so local demos still work.

Required for read-only Jira import:

- `JIRA_ENABLED=true`
- `JIRA_BASE_URL=https://your-site.atlassian.net`
- `JIRA_EMAIL=your-email@example.com`
- `JIRA_API_TOKEN=...`

Optional:

- `JIRA_ACCEPTANCE_CRITERIA_FIELD=customfield_12345`

Requirement analysis is rule-based by default for stable local demos. The same `RequirementAnalysisSynthesizer` boundary can be switched to an LLM-backed implementation without changing the controller, coordinator, artifact model, or frontend workflow. Both paths now expose analyst concerns so requirement intake can flag privacy, role access, performance, and testing questions before impact analysis.

The request can also include connector-style source metadata (`source_type`, `source_name`, `source_url`, `received_at`). These fields are folded into the stored analysis input so an imported Jira ticket, email, or meeting note keeps its origin during the workflow.

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

The primary Software Analyst workflow now exposes this after a handoff summary is reviewed, so the analyst can create a Jira follow-up or Bitbucket PR comment from the final reviewed summary instead of only viewing it in the app.

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
