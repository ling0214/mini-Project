# Architecture

## Product Shape

The current product is a **Software Analyst Workflow Assistant**. The frontend presents one guided workflow instead of separate role selection:

```text
Requirement Intake -> Requirement Analysis -> Clarification -> Review -> Impact Analysis -> Test Scenarios -> Analyst Report
```

The backend still keeps skills and agent profiles as separate concepts, but the primary profile is now `software-analyst`.

## Layers

### 1. Project Graph

The project graph is queried through the local MCP server. Code-oriented tools are backed by `codebase-memory-mcp`, giving the Java backend a reusable graph interface instead of hardcoding code search logic into the application.

### 2. MCP Tool Layer

The Python MCP server exposes:

- `get_endpoint_info(name)`
- `trace_impact(name, max_hops=2)`
- `get_test_coverage(name)`
- `search_issues(query)`

The first three tools query `codebase-memory-mcp`; `search_issues` still reads the local issue fixture.

### 3. Skill Layer

Skills are role-agnostic units of analysis. They call MCP tools and return structured result schemas with evidence.

| Skill | Purpose | Status |
|---|---|---|
| `requirement-analysis` | Extract rules, ambiguity, missing information, assumptions, affected areas | Implemented |
| `impact-analysis` | Identify affected modules, risk notes, effort, missing evidence | Implemented |
| `test-case-gen` | Generate test scenarios and regression checklist | Implemented |
| `code-qa` | Answer grounded codebase questions | Implemented |
| `timeline-estimation` | Estimate delivery timeline from reviewed impact/test artifacts | Implemented |
| `handoff-summary` | Compile reviewed workflow artifacts into a shareable handoff summary | Implemented |

### 4. Agent Profile Layer

The primary profile is `software-analyst`, implemented by `SoftwareAnalystAgent`. It allows the full workflow skill set:

- `requirement-analysis`
- `impact-analysis`
- `test-case-gen`
- `code-qa`
- `timeline-estimation`
- `handoff-summary`

Legacy compatibility profiles still exist in code, but they are no longer the main frontend model.

### 5. Coordinator

`CoordinatorService` is the deterministic workflow layer. It does not run an autonomous LLM planner. It validates the active profile, calls the selected skill, persists the artifact, and enforces review-gated handoff rules.

Key handoffs:

- `POST /api/artifacts/{taskId}/clarify`
  - Source must be `requirement-analysis`.
  - Creates a linked requirement-analysis artifact with additional analyst clarification.

- `POST /api/artifacts/{taskId}/handoff/impact-analysis`
  - Source must be reviewed `requirement-analysis`.
  - Runs impact analysis from the persisted requirement input, preserving clarification text.

- `POST /api/artifacts/{taskId}/handoff/test-case-gen`
  - Source must be reviewed `impact-analysis`.
  - Target must be one of the source artifact's affected module names.

- `POST /api/artifacts/{taskId}/handoff/timeline-estimation`
  - Source must be reviewed `impact-analysis`.
  - Uses linked test-case artifacts when present.

- `POST /api/artifacts/{taskId}/handoff/handoff-summary`
  - Source must be reviewed `impact-analysis`.
  - Uses a reviewed requirement artifact and generated test-case artifacts.
  - Persists the final handoff summary as its own reviewable artifact.

### 6. Persistence

Artifacts are stored in H2 via Spring Data JPA.

Core data:

- `task_id`
- `profile`
- `agent`
- `skill`
- `input_text`
- `result_json`
- `reviewed`
- `reviewed_at`
- `parent_task_id`
- evidence rows

This makes the workflow auditable:

```text
requirement-analysis
  -> clarification requirement-analysis
      -> impact-analysis
          -> test-case-gen
```

### 7. Frontend

The React/Vite frontend is the primary UI. It renders the Software Analyst guided workflow directly on first load and calls the backend using the `software-analyst` profile.

## Deliberate Non-Goals

- No autonomous skill selection.
- No unreviewed downstream handoff.
- No production authentication.
- No multi-repo project selector yet.
- No LLM synthesizer yet; current synthesis is deterministic and testable without API keys.
