---
name: develop
description: Use when adding a new feature, skill, endpoint, or UI capability to the mini-Project repo (backend Spring Boot harness, mcp-server Python tool server, or React frontend) — anything that should reuse the existing Artifact/Coordinator/Agent pattern instead of reinventing it. Trigger on requests like "add a skill for X", "wire up a new endpoint", "extend the frontend to show Y", or generically "/develop". Not for one-off bug fixes with no architectural surface, and not for editing the product's own domain specs at agents/*.md, skills/*.md, profiles/*.md (root-level design docs) — this skill is about writing the code that implements those specs, following the conventions already established in backend/, frontend/, and mcp-server/.
---

# /develop

Guided procedure for adding a feature to mini-Project so it reuses established architecture instead of re-deriving it from scratch. This skill encodes conventions verified against real code in this session — treat it as the up-to-date source of truth over `.claude/agents/mini-project-dev.md`, whose "Working rules" section is stale (still describes the frontend as "only a static prototype" and Week-1 code-qa as "the only slice confirmed working" — both wrong as of this writing; worth fixing that file but it is not this skill's job to do so automatically).

## Step 1 — Ask before doing anything else

Do not start planning or editing until you know:
1. **What** feature/change is wanted, in concrete terms (a new skill? a new endpoint on an existing skill? a UI panel? a bug-adjacent enhancement?).
2. **Which subprojects it touches** — backend only / frontend only / mcp-server only / full stack. Most "real" features touch backend + frontend at minimum (new skill needs a UI panel to invoke it); don't assume, ask.

If the user's initial request already answers both clearly, you can skip re-asking — but if either is ambiguous, ask before reading further code or writing a plan.

## Step 2 — Plan against the conventions below, then confirm before coding

For anything that isn't a trivial one-file tweak, write out a short plan (which files, in what order) and get a quick confirmation before touching code — especially for backend changes that fan across the Artifact/Coordinator/Agent seam, which routinely touches 4-5 files for even a small addition (skill class + synthesizer, coordinator wiring, agent allowlist, controller, request/response records).

### Backend (Spring Boot, Maven, `backend/`)

Worked example to pattern-match against: `RequirementAnalysisSkill`, live in `backend/src/main/java/com/miniproject/backend/`.

1. **Skill logic** lives in `skills/` as a `@Component` with a `run(...)` method returning a result record (e.g. `RequirementAnalysisResult`). If the skill needs code-graph facts, take an `XxxSynthesizer` interface dependency — the swap point for a future LLM-backed implementation. Everything today is rule-based (e.g. `RuleBasedRequirementAnalysisSynthesizer`).
2. **Wire into `coordinator/CoordinatorService.java`** — the *only* place skills get invoked from; controllers never call skills directly. Add the skill as a constructor-injected field, add a method that:
   a. calls `requireSkillAllowed(profile, "skill-name")`
   b. runs the skill
   c. wraps the result: `Artifact.draft(profile + "-agent", "skill-name", result, result.evidence())`
   d. calls `persistence.save(artifact, profile, inputText)`
   e. returns the artifact
3. **Grant the skill to a role** — add the skill-name string to the relevant `Agent` implementation's `allowedSkills()` set in `agent/` (e.g. `BusinessAnalystAgent`). `AgentRegistry` auto-collects every `@Component implements Agent` bean; there is no central registry file to edit.
4. **Expose over HTTP** — add a `@PostMapping` to `web/CodeQaController.java` (despite the name, this is the general `/api/skills/*` controller — existing convention is one controller for skill invocation, don't create a new controller per skill) or to `web/ArtifactHistoryController.java` for anything operating on an existing artifact (`/api/artifacts/{taskId}/...`, e.g. handoffs). Request bodies are small `record`s in `web` (e.g. `RequirementAnalysisRequest(String profile, String description)`).
5. **Persistence is automatic and skill-agnostic** — `ArtifactPersistenceService` stores every `Artifact<T>` as one `AnalysisArtifactEntity` row, `result` as a JSON blob (Jackson). Never add a skill-specific table/entity. `parentTaskId` links a handoff/derived artifact back to its source — see `handoffToTestCaseGen`, `clarifyRequirementAnalysis` for the pattern: load the source via `persistence.findArtifact(taskId)`, validate `source.skill()` and usually `source.reviewed()`, build the new artifact, save with the 4-arg `save(artifact, profile, inputText, parentTaskId)` overload.
6. **Derived/computed fields beyond the raw result** (e.g. `AnalysisStatus` — `NEEDS_CLARIFICATION` vs `READY_FOR_REVIEW`, computed from `missingInformation.isEmpty()`) do NOT go on `Artifact<T>` (shared by every skill). Add a small wrapper response record in `web/` instead — see `RequirementAnalysisResponse(Artifact<RequirementAnalysisResult> artifact, AnalysisStatus status)` with a static `.of(artifact)` factory — and have the controller return the wrapper type.
7. **JSON naming gotcha**: `application.yml` sets `spring.jackson.property-naming-strategy: SNAKE_CASE` globally. Every camelCase Java field (`changeRequest`, `additionalInfo`, `taskId`) serializes/deserializes as snake_case (`change_request`, `additional_info`, `task_id`) automatically — don't hand-roll `@JsonProperty`, and when writing frontend fetch bodies use snake_case keys to match.
8. **Testing**: JUnit 5 + AssertJ. Skill classes get a direct unit test constructing the skill with its real (non-mocked) rule-based synthesizer, asserting on output shape (see `RequirementAnalysisSkillTest`). There is no established mocks-based test convention for `CoordinatorService` itself — don't invent one unprompted; just ensure `mvn test` passes after wiring. **Always run `mvn -q compile` then `mvn test` before calling backend work done — never skip this.**

### Frontend (React + Vite, `frontend/src/main.jsx` — single file, ~1200 lines, no component splitting yet)

- `api(path, options)` is the one fetch helper (JSON in/out, throws on non-2xx) — always reuse it, never call `fetch` directly in a new component.
- Reusable presentational components already exist — use them instead of re-implementing per feature: `SkillForm` (controlled text input + chips + submit, used for every free-text skill input), `EvidenceList`, `SimpleList`, `Stat`, `Tag`, `ErrorBox`, `HeaderBlock`. Look at how `RequirementAnalysisReport` / `ImpactReport` / `TestGenReport` are built before writing a new report component from scratch.
- `styles.css` is one file with an established class vocabulary (`.screen`, `.work-panel`, `.stat-grid`, `.list-section`, `.status-pill`, `.action-row`, `.btn.primary`/`.ghost`, `.chip`, etc.) — reuse existing classes; only add new CSS for genuinely new UI shapes, and note why in a comment if it's not obvious.
- `VITE_API_BASE` env var (default `http://localhost:8080`) points at the backend. Dev loop: `mvn spring-boot:run` in `backend/`, then `npm run dev` in `frontend/` (Vite on `127.0.0.1:5173`).
- Backend responses use snake_case (see gotcha #7 above) — frontend fetch bodies must send snake_case keys, and destructuring backend JSON in JS should expect snake_case too.
- **Before calling frontend work done**: run `npm run build`, or at minimum confirm the Vite dev server hot-reloads with no console/build error.

### mcp-server (Python, `mcp-server/`, `.venv`)

- `mcp_server/server.py` exposes 4 tools (`get_endpoint_info`, `trace_impact`, `get_test_coverage`, `search_issues`) to the Java backend over MCP-over-stdio via `McpToolClient.java`.
- **Known-fixed gotcha — do not regress this**: `get_endpoint_info` / `trace_impact` / `get_test_coverage` are backed by a *second*, separate MCP server (`codebase-memory-mcp`, project slug `C-Users-lingn-mini-Project`) via `mcp_server/cbmm_client.py`. That module deliberately does **not** hold a persistent MCP-over-stdio session to `codebase-memory-mcp.exe` — it shells out per call to `codebase-memory-mcp.exe cli <tool> --args-file <path>` (one-shot mode). This was verified this session: a persistent-session approach (Python `mcp` SDK's `stdio_client()`) hangs indefinitely against this exe on this machine — a raw pipe to the exe answers in ~2ms, but the Python client's `anyio`-backed transport never observes the response. If you are tempted to "simplify" `cbmm_client.py` back to a persistent session, stop — that reintroduces a real, verified hang.
- Tests: pytest under `mcp-server/tests/`, run via `mcp-server/.venv/Scripts/python.exe -m pytest`. `cbmm_client`'s tests mock `_call_tool` directly (`unittest.mock.AsyncMock`) rather than spawning a real subprocess — keep that pattern for fast, deterministic tests. A separate live integration test, `backend/src/test/java/.../McpToolClientIntegrationTest.java` (JUnit), exercises the real subprocess end-to-end — that's the one that actually caught the hang above, so don't remove or weaken it when refactoring `cbmm_client.py`.
- **Before calling mcp-server work done**: run `mcp-server/.venv/Scripts/python.exe -m pytest` under `mcp-server/`.

## Step 3 — Delegate or edit directly, explicitly

Make this call out loud rather than defaulting to one mode:
- **Delegate to the `mini-project-dev` subagent** when the change is multi-file and substantial — e.g. a new skill touching skill class + coordinator + agent allowlist + controller + frontend panel, or anything spanning backend + frontend + mcp-server. Hand it a concrete plan (from Step 2) so it isn't rederiving conventions.
- **Edit directly** for small, localized changes — a single new field on an existing response record, a CSS tweak, a one-line allowlist addition — where spinning up a subagent is overhead, not help.

## Step 4 — Verification gate before declaring done

Never skip these for the subprojects actually touched:
- Backend touched → `mvn -q compile` then `mvn test` (run from `backend/`).
- mcp-server touched → `mcp-server/.venv/Scripts/python.exe -m pytest` (run from `mcp-server/`).
- Frontend touched → `npm run build` in `frontend/`, or at minimum confirm the Vite dev server hot-reloads without error.

Report back which verification commands were actually run and their outcome — "should work" is not a substitute for running the test suite.

## Step 5 — Reindex codebase-memory-mcp

Only after Step 4's verification has actually passed — don't index code whose broken/uncommitted-intent state would poison the graph for the next session. Call `mcp__codebase-memory-mcp__index_repository` with `repo_path` = `c:\Users\lingn\mini-Project`, `mode: "full"`, `persistence: true`. The `persistence: true` flag is what matters here — it's what writes the compressed artifact back to `.codebase-memory/graph.db.zst` so the refresh survives past this session instead of living only in memory. This was a real gap: the index drifted across a full session of edits before anyone thought to refresh it.

Known coverage gap, so a "clean" reindex isn't mistaken for a bug: the indexer does not currently parse JS/JSX — `frontend/src/main.jsx` and `.css` files produce zero graph nodes (verified this session: `search_graph` on a known-real JSX component returned no match). Java, Python, and doc/config files (TOML/HTML/YAML/Markdown) index fine. So this step meaningfully covers backend/ and mcp-server/ edits; a frontend-only change won't show up in the graph afterward, and that's expected, not something to debug.

`mcp__codebase-memory-mcp__detect_changes` is a lighter-weight way to preview what's drifted before committing to a full reindex, if useful — but it doesn't update the persisted index by itself, so the actual refresh still has to end with `index_repository` + `persistence: true`.
