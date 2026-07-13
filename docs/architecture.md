# Architecture

## Naming shortlist

Repo is currently `mini-Project` (placeholder). Candidates to rename to before this goes public on a resume/LinkedIn:

- **Vantage** — "one project, every vantage point." Different roles look at the same project from a different angle.
- **Trellis** — a support structure other things grow on. Matches the extensibility story: new roles attach to the existing graph/skill layer instead of forking the app.
- **Facet** — one project, many facets.
- **Wayfinder** — navigating an unfamiliar codebase.

## Problem this is solving

A Project Analyst taking over an unfamiliar project spends days reconstructing context that's scattered across the repo, the issue tracker, and people's heads. Existing AI dev tools (Cursor, Cody, Greptile, Panto AI) solve the *developer* version of this — "explain this code" — using embedding-similarity search over code. That's a weak fit for PA/Tester questions:

- *"What breaks if I change this endpoint?"* — a call-graph traversal question.
- *"What should I test for this change?"* — needs to know which code paths and which existing issues touch the changed area.
- *"Is this feasible and how big is it?"* — needs the graph plus issue history, not a text summary.

Embedding search answers "what looks similar to this text." It doesn't answer "what depends on this." That distinction is the reason this project builds a **graph**, not a vector index, as the core data structure.

## Layers

### 1. Project graph

Nodes: files, functions/endpoints, issues. Edges: calls, imports, "issue references file/function." Built once per target repo from source parsing (tree-sitter) + GitHub Issues API, refreshed incrementally.

### 2. MCP tool layer (Python)

The graph is exposed as MCP tools, not baked into a single app:

- `get_endpoint_info(name)` — signature, callers, callees, related issues
- `trace_impact(file_or_function)` — everything downstream that a change would touch
- `get_test_coverage(function)` — existing tests, if any, that already exercise this path
- `search_issues(query)` — text + graph-aware issue search

Exposing this as MCP (not a bespoke REST API baked into the harness) means any MCP-speaking client — this app, Claude Desktop, a future Slack bot — can use the same tools without reimplementing them. This is the single biggest differentiator versus a typical "wraps an LLM API" portfolio project: it demonstrates understanding of the tool-standardization problem, not just prompt-calling.

### 3. Skill layer (role-agnostic)

A skill is a prompt template + a fixed subset of MCP tools it's allowed to call + an output schema. Skills don't know about roles.

| Skill | Tools used | Output |
|---|---|---|
| `impact-analysis` | `trace_impact`, `search_issues` | affected modules, risk notes, rough effort, **evidence list** |
| `test-case-gen` | `get_endpoint_info`, `get_test_coverage` | positive/negative/edge test cases |
| `code-qa` | `get_endpoint_info`, `trace_impact` | free-form Q&A grounded in graph facts |
| `weekly-report` | `search_issues` | status summary from issue state deltas |

### 4. Role profiles

A profile = persona prompt + which skills it can invoke + default view. Ships with two profiles (`profiles/project-analyst.md`, `profiles/tester.md`). Adding a Dev or PM profile later is authoring a new profile file, not touching the graph, MCP layer, or existing skills — that's the actual mechanism behind "extensible to other roles," not just a claim in the README.

### 5. Harness (Spring Boot)

Routes an incoming request to the active profile, resolves which skill(s) apply, calls the skill (which calls MCP tools, which query the graph), and — critically — does not let a skill's output be marked "confirmed" without a human clicking through a review step. Every generated artifact carries its evidence (file:line, issue number, commit) alongside the AI's claim, so the reviewer isn't asked to trust the AI blind. This mirrors a rule already proven out in a production incident-response system: no confirmed finding without evidence, and no action taken before a human confirms.

## Why the human-review gate matters here specifically

An impact analysis that's wrong in a way that looks confident is worse than no analysis — it gives a PA false certainty on scope/effort. Every skill output in this app ships with:
- a confidence flag
- an explicit "missing evidence" list when the graph doesn't have enough signal
- links back to the exact graph nodes used, so a reviewer can spot-check instead of re-deriving from scratch

## Phased build plan (3–4 weeks)

1. **Week 1** — target repo ingestion (tree-sitter parse + GitHub Issues pull), graph construction, MCP server exposing `get_endpoint_info` + `search_issues`, `code-qa` skill working end to end. This proves the core "understand an unfamiliar project" story.
2. **Week 2** — `trace_impact` tool + `impact-analysis` skill + evidence/review-gate UI.
3. **Week 3** — `get_test_coverage` tool + `test-case-gen` skill + regression checklist export.
4. **Week 4** — `weekly-report` skill, profile switcher UI, deploy, record demo, write up 3 sample artifacts (impact analysis doc, test case sheet, weekly report) into `docs/samples/`.

## Demo data

A public, moderate-sized open-source repo + its real GitHub Issues — chosen so the demo has real substance without using any employer or confidential codebase.
