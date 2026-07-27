# mini-Project

A **Software Analyst Workbench**, not a chatbot: it helps a **Project Analyst**, **Business Analyst**, and **Tester** pick up an unfamiliar software project fast — understand it, scope change requests, assess risk, generate test cases, and hand work between roles, with every claim backed by an evidence citation and gated behind a human review step. Built to be extended to other roles without touching the core.

> Working name — see [docs/architecture.md](docs/architecture.md) for the naming shortlist and rationale.

## Why this exists

Project Analysts lose days understanding a new codebase because context is scattered across a repo, an issue tracker, and tribal knowledge. Most "AI dev tools" answer *"what does this code do"* with vector-similarity search over code, which is good for developers but weak for PA/BA/Tester questions like *"what breaks if I change this endpoint"* or *"what should I test here"* — those are graph-traversal questions, not similarity questions.

This project answers them with a small **project graph** (code → calls → issues), exposed as an **MCP server**, consumed by a **skill + agent/role-profile harness** with a real database behind it — see [docs/proposal.md](docs/proposal.md) for the full write-up (literature review, market comparison, risk analysis) and [docs/architecture.md](docs/architecture.md) for how it's actually built.

## Architecture at a glance

```
Project Graph (code + issues)
        │
   MCP Tool Layer   ← Python, exposes get_endpoint_info / trace_impact / get_test_coverage / search_issues
        │
   Skill Layer       ← code-qa, impact-analysis, test-case-gen (implemented); weekly-report (design only)
        │
   Agent / Role Layer ← Project Analyst, Business Analyst, Tester — one Agent class per role, deterministic
        │                permission checks + deterministic agent-to-agent handoff (reviewed impact-analysis
        │                → test-case-gen)
        │
   Harness (Spring Boot) → routes requests, enforces the human-review gate, persists every artifact
        │
   Database (H2, file mode) → analysis_artifacts + evidence, survives a restart
```

Full design rationale (why graph-based retrieval over plain RAG, why skills are separate from profiles, why every AI output needs an evidence trail, why the interaction model is a workbench and not a chatbox) is in [docs/architecture.md](docs/architecture.md) and [docs/proposal.md](docs/proposal.md).

## Tech stack

- **Backend / harness**: Spring Boot (Java) — agent/role routing, skill orchestration, human-review gate, persistence, REST API
- **MCP server**: Python — project graph construction (AST parsing + issue ingestion) and MCP tool exposure
- **Database**: H2 (file mode) via Spring Data JPA
- **Frontend**: React (Vite) app is primary ([frontend/src/main.jsx](frontend/src/main.jsx), `npm run dev` from `frontend/`); a vanilla HTML/JS prototype ([frontend/prototype/code-qa.html](frontend/prototype/code-qa.html)) is retained as a reference/fallback
- **Target project (demo data)**: currently a hand-authored sample target (`mcp-server/sample_target/`); a real public open-source repo + its GitHub Issues is planned but not done

## Status

Four skills implemented and verified end-to-end against the real backend: `code-qa`, `impact-analysis` (including a read-only GitHub PR connector), `test-case-gen`, and `timeline-estimation`. Every artifact is persisted with its review state and survives a restart; a history view lists and reopens past artifacts, with a visible lineage link for any handed-off artifact. Two deterministic handoffs run off a reviewed `impact-analysis` artifact: to `test-case-gen` (Tester role), and to `timeline-estimation` (Project Analyst), the latter getting more grounded — not just more confident — once a `test-case-gen` handoff already exists for the same source. A reviewed artifact can also hand off externally — Jira issue creation and Bitbucket PR comments are both **live-verified** against real accounts (Jira ticket KAN-2, Bitbucket comment 828611336), not just dry-run tested, with a persisted audit trail (`GET /api/artifacts/{id}/external-handoffs`). See [docs/architecture.md](docs/architecture.md) for the full layer-by-layer design and [docs/proposal.md](docs/proposal.md) Chapter 10 for the continuously-reconciled phase-by-phase status. Not yet done: `weekly-report`, a real cloned demo repository, multi-language parsing, a Bitbucket *read* connector (PR-as-input only works for GitHub today), and any LLM-based synthesizer (no autonomous skill selection — every route is deterministic).

## Roles supported

| Role | Skills available today |
|---|---|
| Project Analyst | `code-qa`, `impact-analysis`, `timeline-estimation` (all implemented); `weekly-report` (spec only) |
| Business Analyst | `code-qa`, `impact-analysis` (both implemented) — same skills as Project Analyst; scoped to checking a change against what the business asked for, not requirement authoring (BRDs, user stories) |
| Tester | `code-qa`, `test-case-gen` (both implemented) — can also receive a handoff from a reviewed Project Analyst/Business Analyst `impact-analysis` artifact |

New roles are added by defining a profile persona under [profiles/](profiles/) plus one new `Agent` class under `backend/.../agent/` — the graph, MCP tools, and skills are shared, not duplicated.
