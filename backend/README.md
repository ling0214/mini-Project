# backend

Spring Boot harness: deterministic agent/skill routing, the human-review gate, artifact persistence, and the REST API for the frontend. Talks to `mcp-server` as a real **MCP client** over stdio (official `io.modelcontextprotocol.sdk` Java SDK) — not a bespoke HTTP wrapper.

See [docs/architecture.md](../docs/architecture.md) for the full design.

## Status

Four skills implemented end to end and verified live against the real backend:

| Endpoint | Skill | Notes |
|---|---|---|
| `POST /api/skills/code-qa` | `code-qa` | `CodeQaSkill` → `get_endpoint_info`/`search_issues` → `RuleBasedAnswerSynthesizer` |
| `POST /api/skills/impact-analysis` | `impact-analysis` | `ImpactAnalysisSkill` → `trace_impact`/`search_issues` → `RuleBasedImpactAnalysisSynthesizer` |
| `POST /api/skills/impact-analysis/from-pr` | `impact-analysis` | Same skill, input is a public GitHub PR (read-only `GitHubPrReader`, no write-back) instead of typed text |
| `POST /api/skills/test-case-gen` | `test-case-gen` | `TestCaseGenSkill` → `get_endpoint_info`/`get_test_coverage`/`search_issues` → `RuleBasedTestCaseGenSynthesizer` |
| `POST /api/artifacts/{taskId}/handoff/timeline-estimation` | `timeline-estimation` | No MCP tools — derives from a stored `impact-analysis` artifact (+ any linked `test-case-gen` artifacts) via `RuleBasedTimelineEstimationSynthesizer` |

Every result is wrapped in an `artifact.v1` envelope with `reviewed: false` by default, and persisted immediately (see Persistence below).

Each synthesis step is intentionally behind its own interface (`AnswerSynthesizer`, `ImpactAnalysisSynthesizer`, `TestCaseGenSynthesizer`, `TimelineEstimationSynthesizer`). The current rule-based implementations assemble output purely from graph/issue facts, no LLM call — so the whole path is runnable and testable with zero secrets. An LLM-backed implementation of any of these is the planned next step once an Anthropic API key is available (put it in `src/main/resources/application-local.yml`, gitignored — see `application.yml`'s comments; never in a tracked file).

### Agent layer

`backend/src/main/java/com/miniproject/backend/agent/` — an `Agent` interface plus one `@Component` per role (`ProjectAnalystAgent`, `BusinessAnalystAgent`, `TesterAgent`) declaring `allowedSkills()`, collected automatically into `AgentRegistry`. `CoordinatorService` delegates permission checks here instead of a hardcoded map. This is a permission boundary, not an autonomous planner — which skill runs is still decided by which endpoint was called, not by an LLM reading intent.

### Deterministic handoff + lineage

Two handoffs run off a reviewed `impact-analysis` artifact, both requiring the source to be `impact-analysis` and already `reviewed`:

- `POST /api/artifacts/{taskId}/handoff/test-case-gen` — target must be one of the source's own affected-module names. Runs under the `tester` profile.
- `POST /api/artifacts/{taskId}/handoff/timeline-estimation` — runs under the `project-analyst` profile; checks for an existing `test-case-gen` handoff from the same source and, if found, grounds the QA-regression estimate in its real case count instead of a rough per-module guess.

Every handed-off artifact stores `parent_task_id` (the source's `task_id`), surfaced in `GET /api/artifacts` and the frontend History view as a "↳ handoff from {id}…" link.

### External write-back (Jira, Bitbucket)

`com.miniproject.backend.integrations` — `BitbucketConnector`, `JiraConnector`, and `ExternalHandoffService`, exposed at `POST /api/artifacts/{taskId}/external-handoff` and `GET /api/artifacts/{taskId}/external-handoffs`. Same governance as the internal handoffs (source must be `reviewed`), plus its own safety default: every call is **dry-run** unless the caller passes `dry_run: false` explicitly. `JiraConnector` routes through `api.atlassian.com/ex/jira/{cloudId}/...` when `integrations.jira.auth-mode=scoped` (required for Atlassian's newer scoped API tokens — hitting the site URL directly would 401/403 for that token type). **Both paths are live-verified against real accounts**, not just dry-run tested: a real Jira issue (`KAN-2`) and a real Bitbucket PR comment (`828611336`) were created through these exact endpoints, and both are retrievable via `GET .../external-handoffs`. Configure via `integrations.bitbucket.*` / `integrations.jira.*` in `application-local.yml` (gitignored) — see that file's comments for the required fields.

### Persistence

Spring Data JPA + H2 in file mode (`backend/data/`, gitignored) — `analysis_artifacts` + `evidence` tables, survives a restart (verified directly). `GET /api/artifacts` lists history, `GET /api/artifacts/{id}` reopens one, `PATCH /api/artifacts/{id}/review` is the real endpoint "Mark as reviewed" calls.

## Prerequisites

`mcp-server`'s virtualenv must exist first (see [../mcp-server/README.md](../mcp-server/README.md)) — this backend spawns `../mcp-server/.venv/Scripts/python.exe -m mcp_server.server` as a subprocess on startup.

## Run

```bash
mvn spring-boot:run
```

Starts on `:8080`. Startup log should show the MCP handshake:

```
i.m.c.transport.StdioClientTransport  : MCP server started
i.m.client.LifecycleInitializer       : Server response with Protocol: 2024-11-05 ... Info: Implementation[name=mini-project-graph...]
```

## Try it

```bash
curl -s -X POST http://localhost:8080/api/skills/code-qa \
  -H "Content-Type: application/json" \
  -d '{"profile":"project-analyst","question":"If we change charge_card, what breaks?"}'
```

Returns an `artifact.v1` envelope citing `payments.py:1` and `issue #108` from the real graph — not a paraphrase.

## Test

```bash
mvn test
```

9 tests: unit tests for `CodeQaSkill`/`RuleBasedAnswerSynthesizer` against a mocked `ProjectGraphClient`, `ExternalHandoffServiceTest` (reviewed-gate rejection + a dry-run handoff, both mocked — no real network call), plus `McpToolClientIntegrationTest` — which spawns the actual Python `mcp-server` and asserts on real call-graph facts. That integration test is the one that matters most here: it's what proves the Java harness and the Python graph server genuinely interoperate over MCP, not just that each half compiles.

## Config (`src/main/resources/application.yml`)

| Property | Default | Notes |
|---|---|---|
| `mcp.python-executable` | `../mcp-server/.venv/Scripts/python.exe` | Relative to backend's working directory. Override with an absolute path if running from elsewhere. |
| `mcp.server-args` | `-m,mcp_server.server` | Passed to the python executable. |
| `spring.datasource.url` | `jdbc:h2:file:./data/miniproject` | File-mode H2, not in-memory — `backend/data/` is gitignored. |
| `anthropic.api-key`, `openai.api-key` | empty | Reserved property names, not read by any code yet (no LLM synthesizer exists). **Never put a real key here** — this file is tracked by git. Real keys go in `src/main/resources/application-local.yml` (gitignored, auto-imported) or the `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` env vars. |
| `integrations.bitbucket.*`, `integrations.jira.*` | disabled | Real credentials only ever go in `application-local.yml` (gitignored) — see External write-back above. |

## Known limitations (MVP, called out on purpose)

- Only `code-qa`, `impact-analysis`, `test-case-gen`, and `timeline-estimation` are wired up. `weekly-report` has a skill spec in `../skills/` but no backend implementation yet.
- No autonomous skill selection — every route (including all handoffs) is deterministic. An LLM-based planner is future work, not started.
- Bitbucket write-back exists (comment on a PR) but there's no Bitbucket read side — unlike GitHub, a Bitbucket PR can't be used as `impact-analysis` input.
- No auth — this is a local dev harness, not exposed anywhere.
- Single fixed target directory (`MCP_TARGET_DIR`) — no multi-repo/project-selection support yet, so no `projects` table either.
