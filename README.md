# mini-Project

An AI harness that helps a **Project Analyst** and a **Tester** pick up an unfamiliar software project fast — understand it, analyse change requests, generate test cases, and report status. Built to be extended to other roles (Dev, PM) without touching the core.

> Working name — see [docs/architecture.md](docs/architecture.md) for the naming shortlist and rationale.

## Why this exists

Project Analysts lose days understanding a new codebase because context is scattered across a repo, an issue tracker, and tribal knowledge. Most "AI dev tools" answer *"what does this code do"* with vector-similarity search over code, which is good for developers but weak for PA/Tester questions like *"what breaks if I change this endpoint"* or *"what should I test here"* — those are graph-traversal questions, not similarity questions.

This project answers them with a small **project graph** (code → calls → files → issues), exposed as an **MCP server**, consumed by a **skill + role-profile harness** that a Project Analyst or Tester can talk to.

## Architecture at a glance

```
Project Graph (code + issues)
        │
   MCP Tool Layer   ← Python, exposes get_endpoint_info / trace_impact / get_test_coverage / search_issues
        │
   Skill Layer       ← impact-analysis, test-case-gen, code-qa, weekly-report (role-agnostic, reusable)
        │
   Role Profiles      ← Project Analyst, Tester today; Dev/PM are extension points, not rewrites
        │
   Harness (Spring Boot) → routes requests to the right skill(s), enforces a human-review gate on every AI output
```

Full design rationale (why graph-based retrieval over plain RAG, why skills are separate from profiles, why every AI output needs an evidence trail) is in [docs/architecture.md](docs/architecture.md).

## Tech stack

- **Backend / harness**: Spring Boot (Java) — role routing, skill orchestration, human-review gate, REST API
- **MCP server**: Python — project graph construction (code parsing + issue ingestion) and MCP tool exposure
- **Frontend**: React
- **Target project (demo data)**: a public open-source repo + its GitHub Issues, so the demo has real substance without touching any confidential/employer codebase

## Status

Early scaffold — see [docs/architecture.md](docs/architecture.md) for the full design and the phased build plan.

## Roles supported

| Role | Skills available today |
|---|---|
| Project Analyst | `impact-analysis`, `code-qa`, `weekly-report` |
| Tester | `test-case-gen`, `code-qa` |

New roles are added by defining a profile (persona + allowed skills) under [profiles/](profiles/) — the graph, MCP tools, and skills are shared, not duplicated.
