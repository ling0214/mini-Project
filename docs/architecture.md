# Architecture

## Naming shortlist

Repo is currently `mini-Project` (placeholder). Candidates to rename to before this goes public on a resume/LinkedIn:

- **Vantage** — "one project, every vantage point." Different roles look at the same project from a different angle.
- **Trellis** — a support structure other things grow on. Matches the extensibility story: new roles attach to the existing graph/skill layer instead of forking the app.
- **Facet** — one project, many facets.
- **Wayfinder** — navigating an unfamiliar codebase.

## Problem this is solving

A Project Analyst taking over an unfamiliar project spends days reconstructing context that's scattered across the repo, the issue tracker, and people's heads. Existing AI dev tools (Cursor, Cody, Greptile, Panto AI) solve the *developer* version of this — "explain this code" — using embedding-similarity search over code. That's a weak fit for PA/BA/Tester questions:

- *"What breaks if I change this endpoint?"* — a call-graph traversal question.
- *"What should I test for this change?"* — needs to know which code paths and which existing issues touch the changed area.
- *"Is this feasible and how big is it?"* — needs the graph plus issue history, not a text summary.

Embedding search answers "what looks similar to this text." It doesn't answer "what depends on this." That distinction is the reason this project builds a **graph**, not a vector index, as the core data structure. See `docs/proposal.md` for the full literature review, market comparison, and honest job-scope gap analysis — this file stays focused on how the running system is actually built.

## Layers

### 1. Project graph

**Updated 2026-07-22 — engine swap.** The original Week 1 slice built its own graph once per
target directory by AST-parsing every `.py` file (`mcp_server/graph.py`, Python's own `ast`
module — no type resolution, function names assumed unique target-wide). That graph is now only
used for `search_issues`'s issue-loading. `get_endpoint_info`, `trace_impact`, and
`get_test_coverage` are backed instead by **`codebase-memory-mcp`**, a separate, already-running
MCP server that builds a real, multi-language, LSP-based, type-aware call graph (`mcp_server/cbmm_client.py`
makes this server act as an MCP *client* of that other MCP server internally — an MCP-server-of-
MCP-servers composition, not a bespoke integration). This directly resolves three gaps the
original engine explicitly called out in its own docs: name uniqueness assumed, syntactic (not
type-resolved) call matching, and Python-only parsing (codebase-memory-mcp already indexes this
repo's Java/Python/JS/TS mix, not just `.py` files). `search_issues` has no code-graph equivalent
and is unaffected — issues aren't code, so there's nothing for a code graph to model there.

A `TESTS` edge is a first-class edge type in codebase-memory-mcp's graph (built from real
test-framework conventions across languages), which is what makes `get_test_coverage` more
accurate than the original "any function whose name starts with `test`" heuristic — a real
coverage relationship, not a naming-convention guess.

### 2. MCP tool layer (Python)

The graph is exposed as MCP tools, not baked into a single app:

- `get_endpoint_info(name)` — file, line, callers, callees (codebase-memory-mcp-backed)
- `trace_impact(name, max_hops=2)` — blast radius: everything transitively called and everything that transitively calls it, each tagged with hop count and direction (codebase-memory-mcp-backed, via its `trace_path` tool)
- `get_test_coverage(name)` — which tests cover it, via a real `TESTS` graph edge; an empty list is a real coverage gap, not an error (codebase-memory-mcp-backed)
- `search_issues(query)` — keyword match against issue title + body (unchanged, local `issues.json`)

Exposing this as MCP (not a bespoke REST API baked into the harness) means any MCP-speaking client — this app, Claude Desktop, a future Slack bot — can use the same tools without reimplementing them (see `docs/proposal.md` Appendix C for the reusability trade-off this implies). This is the single biggest differentiator versus a typical "wraps an LLM API" portfolio project: it demonstrates understanding of the tool-standardization problem, not just prompt-calling — reinforced by the engine swap above, which proves the same skill/agent/harness layers work unchanged against a completely different graph implementation, because the Java side only ever consumed an untyped `Map<String,Object>` contract in the first place.

### 3. Skill layer (role-agnostic)

A skill is deterministic tool-calling logic + a rule-based synthesizer + an output schema. Skills don't know about roles — a role only decides which skills it's allowed to invoke (Section "Agent layer" below).

| Skill | Tools used | Output | Status |
|---|---|---|---|
| `code-qa` | `get_endpoint_info`, `search_issues` | free-form Q&A grounded in graph facts | **Implemented** |
| `impact-analysis` | `trace_impact`, `search_issues` | affected modules, risk notes, rough effort, **evidence list** | **Implemented** |
| `test-case-gen` | `get_endpoint_info`, `get_test_coverage`, `search_issues` | positive/negative/edge test cases + regression checklist | **Implemented** |
| `timeline-estimation` | none (MCP) — derives from a stored `impact-analysis` artifact + any linked `test-case-gen` artifacts | working-day range + breakdown + basis + assumptions | **Implemented** |
| `weekly-report` | `search_issues` | status summary from issue state deltas | Design only |

`impact-analysis` also has a second, read-only input source: `GitHubPrReader` fetches a public PR's title and changed-file patches over the unauthenticated GitHub REST API and flattens them into the same free-text shape the skill already parses — no write-back to GitHub, ever.

### 4. Role profiles / Agent layer

A profile = persona (`profiles/*.md`) + which skills it can invoke. Three profiles ship today: `project-analyst`, `business-analyst`, `tester`. Permission enforcement lives in code as a small `agent` package (`backend/.../agent/`): an `Agent` interface, one `@Component` per role (`ProjectAnalystAgent`, `BusinessAnalystAgent`, `TesterAgent`) each declaring its `allowedSkills()`, and an `AgentRegistry` that Spring populates automatically from every `Agent` bean. Adding a new role is one new class, not a change to `CoordinatorService`, the graph, or any existing skill — that's the actual mechanism behind "extensible to other roles," not just a claim.

This is a permission boundary, not a planner: which skill method runs is still determined by which REST endpoint was called, not by an agent reading free text and deciding. Autonomous skill selection would need an LLM-based planner, which this codebase does not wire up (see "What this project deliberately does not do" below).

### 5. Deterministic agent-to-agent handoff

A Project Analyst or Business Analyst's `impact-analysis` artifact, once a human marks it `reviewed`, can hand off two ways:

- To the Tester Agent: `POST /api/artifacts/{taskId}/handoff/test-case-gen` runs `test-case-gen` against one of that artifact's affected modules and persists the result under the `tester` profile. Governed by two rules: the source artifact must already be reviewed, and the target must be one of the affected-module names the source artifact actually found (validated server-side).
- To the Project Analyst's own `timeline-estimation`: `POST /api/artifacts/{taskId}/handoff/timeline-estimation` runs a rule-based day-range formula from the same source artifact's affected-module count and risk level. It also checks whether a `test-case-gen` handoff already exists for the same source (via the lineage below) — if so, the real generated-case count grounds the QA-regression estimate instead of a rough per-module guess, and the result is tagged `confidence: high` instead of `medium`/`low`.

Every handed-off artifact records `parent_task_id` = the source artifact's `task_id`, surfaced in the History view as a clickable "↳ handoff from {id}…" link.

### 6. Harness (Spring Boot)

Routes an incoming request to the active profile, checks the `Agent`'s allowed skills, calls the skill (which calls MCP tools, which query the graph), persists the resulting artifact, and — critically — does not let a skill's output be marked "confirmed" without a human clicking through a review step. Every generated artifact carries its evidence (file:line or issue number) alongside the AI's claim, so the reviewer isn't asked to trust the AI blind. This mirrors a rule already proven out in a production incident-response system: no confirmed finding without evidence, and no action taken before a human confirms.

### 7. Persistence

Spring Data JPA over H2 in file mode (`backend/data/`, gitignored) — not in-memory, so artifact and review state survive a restart (verified directly: mark reviewed, kill the process, restart, `reviewed: true` is still there). Two tables: `analysis_artifacts` (one row per artifact; the skill-specific `result` is stored as one JSON blob rather than normalized per skill, since the three skills' results are differently shaped and full normalization would need a table per skill for no query benefit yet) and `evidence` (one row per citation, `@ManyToOne` back to its artifact — normalized because every skill shares that shape). `GET /api/artifacts` lists history, `GET /api/artifacts/{id}` reopens one, `PATCH /api/artifacts/{id}/review` is what "Mark as reviewed" actually calls.

### 8. Frontend

A vanilla HTML/JS prototype (`frontend/prototype/code-qa.html`), not React yet (Section 9.3/Known Limitations in `docs/proposal.md`). Deliberately not a chat window: role → skill tabs → a structured form per skill → a report view, plus a History view — see `docs/proposal.md` Section 4.1 for why a chatbox is the wrong interaction model for this user base.

## Why the human-review gate matters here specifically

An impact analysis that's wrong in a way that looks confident is worse than no analysis — it gives a PA false certainty on scope/effort. Every skill output in this app ships with:
- a confidence flag
- an explicit "missing evidence" list when the graph doesn't have enough signal
- links back to the exact graph nodes used, so a reviewer can spot-check instead of re-deriving from scratch

## What this project deliberately does not do

- **No autonomous skill selection.** Every route above is deterministic — the caller picks the skill (or the handoff rule picks it, per fixed governance rules), an LLM never decides "which skill best answers this." Building that needs an LLM-based synthesizer and an API key, neither of which exists in this codebase yet.
- **No unreviewed external write-back.** `GitHubPrReader` is still GET-only for GitHub PR analysis. Jira issue creation and Bitbucket Cloud PR comments exist only behind `POST /api/artifacts/{taskId}/external-handoff`, which refuses to run until the source artifact is marked reviewed. Both connectors default to dry-run / disabled unless credentials are supplied through local config or environment variables.
- **No multi-repo support.** The MCP server points at one fixed target directory via `MCP_TARGET_DIR`. A `projects` table only becomes real once a project-selection flow exists.

## Actual build history (vs. the original 3–4 week plan)

The original plan below was written before any code existed; keeping it verbatim next to what actually shipped is more useful than quietly rewriting history. See `docs/proposal.md` Chapter 10 for the authoritative, continuously-reconciled phase table.

1. **Week 1 (as planned)** — graph construction, MCP server exposing `get_endpoint_info` + `search_issues`, `code-qa` skill working end to end.
2. **Week 2 (as planned, tool built differently)** — `impact-analysis` skill shipped, but backed by a new `trace_impact` MCP tool that does the multi-hop BFS server-side in Python, not by the Java layer calling `get_endpoint_info` repeatedly as originally sketched.
3. **Week 3 (as planned)** — `get_test_coverage` tool + `test-case-gen` skill + regression checklist, reusing the call graph itself rather than a separate coverage-scanning pass.
4. **Not in the original plan, added because they were the smallest way to prove real capabilities**: a read-only GitHub PR connector; Spring Data JPA + H2 persistence with a review-status API and history view; a formal `Agent`/`AgentRegistry` layer; deterministic agent-to-agent handoff from a reviewed `impact-analysis` to `test-case-gen`; reviewed-only external handoff records with Jira and Bitbucket Cloud connector stubs that can run as dry-run without credentials.
5. **Not yet done**: `weekly-report`, a real cloned public demo repository (still the hand-authored `sample_target`), a React frontend, multi-language parsing, GitHub write-back, and any LLM-based synthesizer.

## Demo data

Currently a hand-authored sample target (`mcp-server/sample_target/`), not yet a real public open-source repo + its GitHub Issues. Swapping to a real repo (and fixing whatever parser gaps that surfaces) is planned but not done — see `docs/proposal.md` Chapter 10, Phase 4.
