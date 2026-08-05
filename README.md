# mini-Project

Software Analyst Workflow Assistant: a review-gated workbench that helps an analyst move from external work item intake to requirement analysis, clarification, impact analysis, test scenarios, and a compiled analyst report — then tracks that ticket's real progress through development, testing, and handoff, including handoffs to an external incident-response system (Hermes).

The project is not trying to recreate Claude Skills. It uses a small skill layer, MCP-backed project graph, artifact persistence, and human review gates to support the repetitive workflow around software analysis.

## Workflow

```text
Analyst Inbox
        |
Ticket Review
        |
Requirement Analysis
        |
Clarification Loop
        |
Human Review Gate
        |
Impact Analysis
        |
Test Scenario Generation
        |
Analyst Report / Handoff
        |
Ticket Tracker + Kanban Board  <- merges mini-Project's own review phases
        |                         with real status reported back by Hermes
Jira / Bitbucket / Hermes (external systems)
```

## Architecture

```text
Connected Project Workspace (declared local repo path, switchable)
        |
Codebase-memory MCP graph + Graphify diagram indexer
        |
Connector-style Analyst Inbox (manual, Jira, Google Calendar, Gmail, CSV import)
        |
Skill Layer (requirement-analysis, impact-analysis, test-case-gen, code-qa, timeline-estimation, handoff-summary)
        |
Software Analyst Agent Profile (permission boundary only, not a planner)
        |
Coordinator (deterministic routing) / Review Gate / Artifact Persistence
        |
React Workflow UI  +  External Handoff (Jira, Bitbucket, Hermes)
        |
Hermes status bridge (bidirectional) -> Ticket Tracker / Kanban Board
```

## Implemented

### Core analyst skill pipeline
- `requirement-analysis`: extracts business rules, ambiguities, missing information, assumptions, analyst concerns, scope clues, confidence, and evidence.
- Optional LLM-backed analysis: the same skill boundary can run rule-based (default, deterministic, repeatable demos), OpenAI Responses API, a local model (Ollama/LM Studio, for restricted/air-gapped codebases), or the Claude CLI — selected via `analysis.llm.provider`.
- Structured clarification tracking: turns missing information and analyst concern questions into answerable items, then creates a linked, append-only `requirement-analysis` artifact chain.
- `impact-analysis`: identifies affected modules, risk notes, rough effort, missing evidence, confidence, and evidence, grounded in the indexed project code graph. Can also run from a public GitHub PR (title + changed-file patches, GET-only) instead of free text.
- `test-case-gen`: generates positive, negative, and edge test scenarios from reviewed impact analysis modules.
- Test scope management: analysts can accept, reject, edit, prioritize, and review generated cases as a linked `test-scope-review` artifact.
- `timeline-estimation`: derives delivery estimates from reviewed impact artifacts and linked test artifacts.
- `handoff-summary`: compiles reviewed requirement, impact, and managed test scope artifacts into a persisted handoff summary.
- Similar-past-change memory: requirement and impact analysis surface prior similar analyst decisions from persisted artifact history.
- Artifact history: every skill run is persisted with evidence, review state, and parent-child lineage; nothing downstream can act on an unreviewed artifact.

### Project workspace & code understanding
- Multi-project workspace: analysts declare and switch between connected local repos; the active workspace drives every downstream feature (indexing, diagrams, ticket scoping, Hermes matching).
- Codebase-memory MCP indexing per workspace, with re-index on demand.
- Graphify deep-flow indexing: recursive whole-tree source discovery (not a fixed folder allowlist), with a subfolder picker when a declared workspace root holds multiple sub-projects (e.g. separate frontend/backend repos) that aren't directly indexable together.
- Project Overview page: architecture diagram plus a per-endpoint sequence diagram (Laravel/Spring routes, controllers, and frontend `api()` calls), generated from either a basic route scanner or the Graphify dependency graph, with an "Archify" presentation-ready export view and an endpoint-grounded AI Q&A panel.

### External handoff & integrations
- Jira: read-only issue import into the Analyst Inbox, plus issue creation/commenting as a controlled, reviewed external handoff.
- Bitbucket, Hermes: controlled external handoff destinations from a reviewed handoff summary.
- Hermes bidirectional bridge: outbound handoff notifies Hermes; Hermes reports real progress back (`Sent to Hermes` → `Hermes accepted` → `Developer update` → `Testing decision` → `Close summary`) via `POST /api/hermes/status`, scoped to the correct project by local path (not display name, which analysts can rename) and matched across parent-repo/subfolder workspace splits. Also relays Hermes's own similar-past-incident RAG check when available.
- Google Calendar / Gmail: read-only import into the Analyst Inbox when connected via OAuth.
- CSV ticket import as an additional manual intake path.

### Tracking & monitoring (real data, no placeholders)
- Ticket Tracker: groups an analyst's own artifact chain into a 6-phase view (Requirement Review, Impact Analysis, Development/Fixing, Testing, Review/Handoff, Jira/UI Sync), scoped to the active project.
- Hermes Incident Tracker: shows Hermes-originated incidents' real reported status end to end, independent of mini-Project's own artifact chain (a ticket routed straight to Hermes for RCA never gets its own impact-analysis artifact, and the tracker reflects that instead of showing it as stuck).
- Kanban board: merges both of the above into one board — a mini-Project ticket only appears once its Requirement Review phase is reviewed, a Hermes incident only appears once Hermes has accepted it — with only genuinely real fields (id, title, source, current stage, last-updated time; no fabricated priority/owner/progress numbers).

### Frontend
- React (Vite) guided workflow UI, with a sidebar split between **Workspace** (real-data features listed above) and **Enhancement Lab** (see below).

## Enhancement Lab (prototype UI, not yet wired to real data)

These pages exist in the frontend for design/demo purposes but still render hardcoded sample data, not a live backend:

- Memory Center (similar past changes)
- Testing Sync (pass/fail and Jira updates)
- Evidence Gate (RCA readiness checklist)
- DB Checks (evidence request flow)

They are kept visually separate from Workspace precisely so it's obvious which parts of the UI are demo-only.

## Main Paths

- Backend: [backend](backend)
- Frontend: [frontend](frontend)
- MCP server: [mcp-server](mcp-server)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Proposal: [docs/proposal.md](docs/proposal.md)
- Software Analyst profile: [profiles/software-analyst.md](profiles/software-analyst.md)
- Skill specs: [skills](skills)
- Coordinator agent spec: [agents/coordinator-agent.md](agents/coordinator-agent.md)

## Run

Start backend:

```powershell
cd backend
mvn spring-boot:run
```

Start frontend:

```powershell
cd frontend
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

The frontend calls the backend at `http://localhost:8080` by default.

## Current Scope

This is a deterministic workflow assistant by default (`agents/coordinator-agent.md`: no autonomous multi-step planning, no LLM-based intent classification — which REST endpoint is called decides which skill runs), with an optional LLM-backed analysis provider. Analysts can now connect and switch between multiple real project workspaces, instead of a single fixed sample project.

Known gaps: production auth is not implemented; the four `Agent` role classes in code (`software-analyst`, plus three legacy `business-analyst`/`project-analyst`/`tester` profiles) are not all documented in `profiles/`; `skills/` documents 4 of the 6 real skill implementations (missing `requirement-analysis.md` and `timeline-estimation.md`); and the Enhancement Lab pages listed above are UI mockups pending real backend wiring.
