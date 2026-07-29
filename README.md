# mini-Project

Software Analyst Workflow Assistant: a review-gated workbench that helps an analyst move from external work item intake to requirement analysis, clarification, impact analysis, test scenarios, and a compiled analyst report.

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
Analyst Report
```

## Architecture

```text
Project Graph + Issues
        |
Connector-style Analyst Inbox
        |
Sample Target Project Context
        |
MCP Tool Layer
        |
Skill Layer
        |
Software Analyst Agent Profile
        |
Coordinator / Review Gate / Artifact Persistence
        |
React Workflow UI
```

## Implemented

- `requirement-analysis`: extracts business rules, ambiguities, missing information, assumptions, analyst concerns, scope clues, confidence, and evidence.
- Optional LLM-backed requirement analysis: the same skill boundary can use OpenAI Responses API when enabled, while the default remains rule-based for repeatable demos. The LLM prompt now asks for privacy, security, role access, performance, compliance, and testing concerns.
- Hermes-style Analyst Inbox: lets the analyst select Jira, email, meeting-note, or manual work items before the AI workflow starts.
- Manual/sample ticket review: captures source metadata, ticket key, title, priority, reporter, description, acceptance criteria, and comments before analysis.
- Jira read-only import: imports a Jira issue into Analyst Inbox when credentials are configured; otherwise keeps the sample dry-run importer for demos.
- MyBanjirCare sample project context: grounds ticket impact analysis in a Laravel/PHP FYP project domain.
- Codebase-memory backed project context retrieval: impact analysis now asks the indexed MyBanjirCare code graph for relevant methods/classes/files, then supplements with local repository evidence.
- Structured clarification tracking: turns missing information and analyst concern questions into answerable items, then creates a linked requirement-analysis artifact.
- Requirement-to-impact handoff: reviewed requirement artifacts become the source of truth for impact analysis.
- `impact-analysis`: identifies affected modules, risk notes, rough effort, missing evidence, confidence, and evidence.
- `test-case-gen`: generates positive, negative, and edge test scenarios from reviewed impact analysis modules.
- Test scope management: analysts can accept, reject, edit, prioritize, and review generated cases as a linked `test-scope-review` artifact.
- `timeline-estimation`: derives delivery estimates from reviewed impact artifacts and linked test artifacts.
- `handoff-summary`: compiles reviewed requirement, impact, and managed test scope artifacts into a persisted handoff summary.
- Summary external handoff: reviewed handoff summaries can be sent to Jira or Bitbucket through the controlled external handoff flow.
- Artifact history: every skill run is persisted with evidence, review state, and parent-child lineage.
- React frontend: primary UI is the Software Analyst guided workflow.

## Main Paths

- Backend: [backend](backend)
- Frontend: [frontend](frontend)
- MCP server: [mcp-server](mcp-server)
- Architecture: [docs/architecture.md](docs/architecture.md)
- Proposal: [docs/proposal.md](docs/proposal.md)
- Software Analyst profile: [profiles/software-analyst.md](profiles/software-analyst.md)

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

This is a deterministic workflow assistant by default, with an optional LLM-backed requirement-analysis provider. It does not yet include autonomous LLM planning, production auth, multi-repo project selection, or live issue ingestion.

The current project-context demo uses `MyBanjirCare` as a fixed sample target project. Impact analysis now retrieves codebase-memory matches first, then supplements them with matching files from the configured local repository path. The next version should add a repository selector and store previous analyst decisions as reusable memory.
