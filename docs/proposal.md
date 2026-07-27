# Evidence-Driven AI Software Change Intelligence Platform

### A Graph- and Evidence-Based Analysis Tool for Software Delivery Analysts

*(Project Analysts, Business Analysts, and Software Testers)*

| Field | Detail |
|---|---|
| Prepared by | Ling |
| Type | Internship Mini Project |
| Version | 2.3 |
| Date | 20 Jul 2026 |
| Status | Four skills implemented end-to-end (code-qa, impact-analysis, test-case-gen, timeline-estimation), persistence + agent-to-agent handoff with lineage verified, external write-back to both Jira (ticket KAN-2) and Bitbucket (PR comment 828611336) live-verified with persisted audit records, a React/Vite frontend now the primary UI; formal proposal for supervisor review |

---

## Revision History

| Version | Date | Author | Description |
|---|---|---|---|
| 1.0 | Initial draft | Ling | Initial proposal — Project Analyst & Tester roles, framed around a single `code-qa` Q&A skill |
| 1.1 | Mid-review revision | Ling | Verified market comparison (Greptile, CodeGraph, Understand Anything) replacing unverified claims; added Vision/roadmap framing; reconciled proposal text against actual running code |
| 2.0 | 19 Jul 2026 | Ling | Restructured into formal proposal format; added Business Analyst as a third role; added target-user job-scope gap analysis; added risk analysis; reorganised future work by time horizon |
| 2.1 | 20 Jul 2026 | Ling | Implemented `impact-analysis`, `test-case-gen`, `timeline-estimation` (Sections 5.2, 6, 5.8); added a read-only GitHub PR connector (5.5); added persistence with review-state durability (5.6); added a formal `Agent`/`AgentRegistry` layer and two deterministic, review-gated handoffs with lineage tracking (5.7, 5.8); identified a pre-existing Bitbucket/Jira write-back connector layer, at that point built and tested but not yet wired to a REST endpoint |
| 2.2 | 20 Jul 2026 | Ling | The Bitbucket/Jira connector layer from 2.1 is now fully wired (`POST /api/artifacts/{taskId}/external-handoff`, frontend Publish panel) and live-verified for Jira: a real Jira issue (KAN-2) was created from a reviewed `impact-analysis` artifact against the author's actual Jira Cloud site, confirming the scoped-API-token gateway routing (`api.atlassian.com/ex/jira/{cloudId}/...`) works, not just that it compiles (Section 5.9) |
| 2.3 | 20 Jul 2026 | Ling | Bitbucket write-back independently live-verified via the same audit trail (a real PR comment, ID 828611336, on `miniproject2026/internproject_frontend#1`) — an initial scan missed the record and had to be re-run before this could be confirmed, corrected here rather than left as a false negative; identified and verified a working React/Vite frontend (`frontend/src/main.jsx`) now serving as the primary UI, covering every backend endpoint this proposal documents, with the vanilla HTML/JS prototype retained as a fallback (Section 5.10) |

---

## Table of Contents

1. Introduction
2. Background Study
3. Literature Review & Market Analysis
4. Proposed Solution
5. System Architecture
6. Functional Design (Workflow View)
7. AI Technologies Applied
8. Development Methodology
9. Project Scope
10. Implementation Plan
11. Demonstration Scenario
12. Evaluation Plan
13. Risk Analysis
14. Known Limitations
15. Future Work
16. Conclusion

References
Appendices

---

## 1. Introduction

### 1.1 Background

Software delivery teams rely on people who understand a system well enough to scope a change, judge its risk, and verify it — without necessarily writing the code themselves. Project Analysts, Business Analysts, and Software Testers all sit in this position at different points in the delivery lifecycle. Their work depends on information that is scattered across source code, issue trackers, documentation, and the memory of whoever has been on the project longest.

### 1.2 Motivation

Mainstream AI coding assistants (GitHub Copilot, Cursor, Claude Code) and even the newer graph-based code-intelligence tools built specifically for AI agents (see Chapter 3) are built for a developer sitting in an IDE, writing code. None of them are built for someone whose job is to *decide* whether a change is safe, *not* to write it. That gap — evidence-backed change understanding for non-coding delivery roles — is what this project addresses.

### 1.3 Problem Statement

1. **Time-consuming project understanding** — Project Analysts, Business Analysts, and Testers routinely spend days understanding an unfamiliar codebase before they can do their actual job (scoping, validating, testing).
2. **Limitation of current AI tools** — existing tools answer "what does this code do," which is a developer's question. They do not answer "what breaks if I change this," "does this match what the business asked for," or "what should I test," which are relationship-traversal questions, not text-similarity questions.
3. **Lack of evidence and governance in AI outputs** — AI-generated answers can look confident while being wrong. In a professional delivery context, a claim without a citation back to a file, line, or issue number cannot be trusted or acted on, and no AI output should be treated as approved without a human reviewing it.

### 1.4 Objectives

1. Build a project knowledge graph that represents code structure **and** historical issue/incident data as one connected graph, not two separate lookups.
2. Expose that graph as standardised MCP tools, so the same tools can be reused by this platform, other MCP-speaking clients, or future skills, without duplication.
3. Implement one complete, evidence-grounded skill (`code-qa`) end-to-end, including a mandatory human-review gate, as proof that the architecture works before building the rest.
4. Demonstrate that the same graph, tools, and skill layer serve three distinct roles (Project Analyst, Business Analyst, Software Tester) through configuration, not re-implementation.
5. Honestly document what is delivered in this iteration versus what remains a future direction, so the proposal's claims match the running system.

### 1.5 Scope

Full scope detail is in Chapter 9. In summary: this iteration delivers a working project graph, an MCP tool layer (`trace_impact`, `get_test_coverage`), four implemented skills (`code-qa`, `impact-analysis`, `test-case-gen`, `timeline-estimation`) fusing code structure with issue history, a read-only GitHub PR connector, a live-verified Jira write-back path (Section 5.9), persistent storage with handoff lineage for every artifact and its review state (Section 5.6/5.7), three configured role profiles, a workflow-per-skill frontend rather than a single chat surface (Section 4.1), and a human-review-gated output contract. It does **not** deliver LLM-based answer generation, a production deployment, multi-repo/`projects` support, a Bitbucket read connector, or the `weekly-report` skill beyond design — these are explicitly future work (Chapter 15).

---

## 2. Background Study

### 2.1 Where these roles sit in the SDLC

Across a typical Software Development Life Cycle (Requirement → Analysis → Impact Analysis → Development → Testing → Deployment → Production Support), Business Analysts and Project Analysts are most active at Requirement and Impact Analysis stages, Testers at the Testing stage, and all three re-appear at Production Support when something breaks and needs to be traced back to a change. Developers are the only role who touch Development directly. This project targets the roles that *consume* an understanding of the system rather than the role that authors it.

### 2.2 AI-assisted software engineering

The dominant pattern in AI coding tools today is retrieval-augmented generation (RAG) over embeddings: a query is matched to similar-looking code by vector similarity, and an LLM writes an answer from the retrieved snippets. This is effective for "explain this function" but weak for relationship questions ("what depends on this"), because similarity is not the same relationship as calls, imports, or ownership.

### 2.3 Knowledge graphs and graph-based retrieval

A knowledge graph represents a codebase as nodes (functions, files, classes, issues) connected by typed edges (calls, imports, references). Traversing this graph answers dependency questions directly instead of approximating them through similarity search. This project's graph is deliberately narrow in this iteration — functions and call edges from AST parsing, plus issues linked by keyword — rather than a fully general-purpose "GraphRAG" system. It is worth being precise here: this project does not yet combine graph retrieval with LLM generation (which is what "GraphRAG" typically refers to in the literature); the current answer synthesis is deterministic and rule-based, described in Section 5.4 and Chapter 9.

### 2.4 Model Context Protocol (MCP)

MCP is an open protocol (Anthropic) for exposing tools, resources, and prompts to AI clients over a standard interface, rather than each application building bespoke tool-calling integrations. Building this project's graph access as MCP tools means the same tools are callable by this platform's own backend, by Claude Code or Cursor directly, or by a future client, without rewriting the graph layer — a property verified in this project's own architecture (Chapter 5) and explored concretely in Appendix C.

### 2.5 Human-in-the-loop and evidence grounding

Human-in-the-loop design treats AI output as a draft, not a decision, until a person confirms it. Evidence grounding requires every claim to carry a citation (file:line, issue number) so a reviewer can verify a claim in seconds rather than re-deriving it from scratch. Both principles are structural in this project, not aspirational: every output is wrapped in an `Artifact` with a `reviewed` flag defaulting to `false`, and every claim carries a typed `Evidence` entry (Section 5.4).

### 2.6 Software change impact analysis

Impact analysis — determining what else is affected by a proposed change — is traditionally manual: reading code, asking developers, searching an issue tracker. This project's contribution is treating impact analysis as a graph-traversal problem enriched with historical incident data, so the "what's affected" question and the "has this broken before" question are answered together, from one query.

---

## 3. Literature Review & Market Analysis

### 3.1 Comparable tools

Three tools were identified and verified (via direct research, not assumption) as real, active projects that validate graph-based code intelligence as a legitimate technical direction:

| Tool | Feature Set | Limitation Relative to This Project |
|---|---|---|
| **[Greptile](https://www.greptile.com/)** — YC-backed, $25M Series A (Benchmark, Sept 2025), $180M valuation | Builds a full semantic code graph (calls, classes, cross-module dependencies); traces change impact during PR review; 82% bug-catch rate in independent benchmarks | Graph is code-only — no fusion with historical issue/incident data; built for a developer reviewing a PR, not a non-coding analyst |
| **[CodeGraph](https://github.com/colbymchenry/codegraph)** — open source, MIT-licensed, ~53.9k GitHub stars | Pre-indexes a codebase into a local knowledge graph consumable by Claude Code, Cursor, Codex, and other coding agents; reduces tool calls/token usage | Same code-only graph limitation; no governance/review-state data model; designed to sit inside a developer's coding-agent session |
| **[Understand Anything](https://github.com/Egonex-AI/Understand-Anything)** — Claude Code plugin, multi-agent pipeline | Builds a project knowledge graph with a persona-adaptive dashboard (its persona model already includes non-developer, business-domain views); has an impact-analysis-of-diffs command | Still a code-only graph with no issue-history fusion; delivered as an IDE/coding-agent plugin, assuming the user already works inside a developer tool; no explicit unreviewed/reviewed data contract |
| **Proposed System** | Code structure **fused with issue-tracker history** in one graph; structured `Artifact` with a `reviewed: false` default and typed evidence per claim, not just a dashboard; delivered as a standalone web app for users who do not install a coding agent | Narrower graph fidelity today (single-language, syntactic call resolution — see Chapter 14) |

All three confirm the technical direction (graph over embedding-similarity) but share one assumption this project deliberately breaks: that the graph should be code-only and the consumer is a developer inside a coding tool.

### 3.2 Target user analysis — does this actually match the job?

Rather than assert that this platform serves Project Analysts, Business Analysts, and Testers, their published job responsibilities were mapped directly against what this system does and does not do. The honest result:

| Job Responsibility Area | Project Analyst | Business Analyst | Software Tester |
|---|---|---|---|
| Requirements & impact analysis | ✅ Directly served (`impact-analysis`, implemented) | ✅ Directly served — "conduct impact analysis" is explicitly shared with PA | — |
| Risk / production issue tracking | 🟡 Partially — code-related risk only, not budget/resourcing risk | — | 🟡 Historical defect visibility informs regression scope |
| Test planning & regression scope | — | — | ✅ Directly served (`test-case-gen`, implemented) |
| Project planning & coordination (scheduling, meetings, milestones) | ❌ Not addressed — this is calendar/PM-tool work, not a graph problem | — | — |
| Stakeholder communication | ❌ Not addressed directly | ❌ Not addressed directly | — |
| Requirement authoring (BRD, user stories, wireframes, process flow) | — | ❌ Not addressed — this is a different problem (structuring human-gathered requirements), not a code-graph problem | — |
| Quality/UAT coordination | — | — | ❌ Not addressed — scheduling/coordination work |

**Honest conclusion:** this system is not a general-purpose assistant for any of these three jobs. It is a specialist tool for the specific recurring moment in each job where understanding *what the code actually does and has historically broken* is required — which is a real, evidence-backed subset of each role, not the whole of it. This is reflected directly in the role profiles (Section 5.3) and is why the platform is positioned as **Software Delivery Analysts** collectively, rather than as a claimed full replacement for any one job title.

---

## 4. Proposed Solution

**Problem:** Software Delivery Analysts (PAs, BAs, Testers) need to answer relationship and history questions about a codebase they did not write, and need every answer to be verifiable, not just plausible.

**Solution:** A project knowledge graph fusing code structure and issue history, exposed as standardised MCP tools, consumed by a role-aware skill layer that always cites its evidence and never lets an output be treated as final without a human confirming it.

**Expected benefit:** less time spent manually cross-referencing code and issue trackers before a change can be scoped, tested, or validated against the original requirement — with every AI-produced claim traceable back to its source, so trust in the output does not depend on trusting the model.

*(Architecture is intentionally deferred to Chapter 5 — this chapter states the idea only.)*

### 4.1 Interaction Model: Workbench, Not Chatbot

A free-text chat window is the wrong interaction model for this user base, and this is a deliberate design decision, not a placeholder waiting to be replaced. A chatbox implies the value is in a conversation; this platform's value is in the **artifact** a Project Analyst, Business Analyst, or Tester walks away with — an impact analysis, a regression checklist, an evidence-cited answer — each independently reviewable and exportable, not buried in a scrollback.

Concretely, each skill is surfaced as a distinct, button-triggered workflow with its own structured input (a change-request form, not an open prompt box) and its own report-shaped output, per Chapter 6. Free-text entry still exists inside a given workflow's input field (Section 6, "extract candidate identifiers"), but the frontend's primary navigation is skill-by-skill, not a single message thread — consistent with the `code-qa` skill already being a bounded, single-purpose action rather than open-ended chat (Chapter 11). A conversational "ask a follow-up" affordance may sit alongside a result as a secondary aid once more skills exist, but it is not the platform's primary surface.

---

## 5. System Architecture

The system consists of four layers, each with a single responsibility:

| Layer | Technology | Responsibility |
|---|---|---|
| Knowledge Graph + MCP Server | Python | Parse source code, construct call graph, ingest issues, expose graph queries as MCP tools |
| AI Harness (Backend) | Java, Spring Boot | Role validation, skill selection, MCP client communication, evidence assembly, review status management |
| Frontend | React (Vite) app — primary as of Section 5.10; a vanilla HTML/JS prototype is retained as reference/fallback | Role selection, per-skill workflow forms, evidence display, review + handoff controls (workbench layout — see 4.1, not a single chat thread) |
| Target Project (Demo Data) | Hand-authored sample target today; a public open-source repo is the stated target | Provides code, endpoints, and issues for demonstration |

### Architecture flow

```
User (Project Analyst / Business Analyst / Tester)
        │
        ▼
┌─────────────────────────────────┐
│  Frontend                       │
│  Role selection, question input,│
│  evidence display, review gate  │
└──────────────┬──────────────────┘
               │ REST API
               ▼
┌─────────────────────────────────┐
│  Spring Boot Harness            │
│  ┌───────────┐ ┌──────────────┐ │
│  │ Role      │ │ Skill        │ │
│  │ Profiles  │ │ Orchestrator │ │
│  └───────────┘ └──────┬───────┘ │
│                       │ MCP     │
│  ┌────────────────────┴───────┐ │
│  │ MCP Client                 │ │
│  └────────────┬───────────────┘ │
└───────────────┼─────────────────┘
                │ MCP Protocol
                ▼
┌─────────────────────────────────┐
│  Python MCP Server              │
│  ┌──────────────────────┐       │
│  │ Project Knowledge    │       │
│  │ Graph                │       │
│  │ (code → calls →      │       │
│  │  files → issues)     │       │
│  └──────────────────────┘       │
│  Tools:                         │
│  - get_endpoint_info            │
│  - search_issues                │
└─────────────────────────────────┘
                ▲
                │ parses
                ▼
┌─────────────────────────────────┐
│  Target Repository              │
│  Source code + Issues           │
└─────────────────────────────────┘
```

### 5.1 MCP Tool Definitions

| MCP Tool | Input | Output |
|---|---|---|
| `get_endpoint_info` | Function or endpoint name (e.g. `checkout_endpoint`) | File, line, functions it calls, functions that call it |
| `search_issues` | Keyword or component name | Matching issues with title, state, and linked files |
| `trace_impact` | Function/entry point name, optional `max_hops` (default 2) | Blast radius: everything it transitively calls and everything that transitively calls it, each tagged with hop count and direction |
| `get_test_coverage` | Function/endpoint name | Which `test_*` functions already in the graph call it directly — an empty list is a real coverage gap, not an error |

### 5.2 Skill Definitions

Skills are reusable task definitions that are not tied to a single role:

| Skill | Scope | Status |
|---|---|---|
| Code & Issue Question Answering (`code-qa`) | Answer questions about structure, dependencies, and related issues with evidence | **Implemented** |
| Change Impact Analysis (`impact-analysis`) | Multi-hop dependency expansion (`trace_impact`) + aggregated historical-issue lookup + a rule-based risk tag | **Implemented** |
| Test Case Generation (`test-case-gen`) | Positive/negative/edge cases templated from the call graph (`get_endpoint_info`, `get_test_coverage`) + a regression checklist from related historical issues | **Implemented** |
| Timeline Estimation (`timeline-estimation`) | Rule-based day-range estimate from a reviewed impact-analysis artifact, grounded further by any linked test-case-gen artifact (Section 5.8) | **Implemented** |
| Weekly Report (`weekly-report`) | Status summary from issue state deltas | Design only |

### 5.3 Role Profiles

| Role | Allowed Skills | Purpose |
|---|---|---|
| Project Analyst | `code-qa`, `impact-analysis`, `timeline-estimation`, `weekly-report`* | Scope change requests, check known risk hotspots, prepare analysis and timeline support |
| Business Analyst | `code-qa`, `impact-analysis` | Check whether a change matches what the business asked for — shares `impact-analysis` with the PA profile rather than duplicating it |
| Software Tester | `code-qa`, `test-case-gen` | Find regression scope, ground test planning in real defect history; can also receive a handoff from a reviewed PA/BA `impact-analysis` artifact |

\* Designed, not yet implemented — see Section 5.2 and Chapter 15.

Role profiles are configuration, not code duplication: adding the Business Analyst role required one persona document (`profiles/business-analyst.md`) and one new `Agent` class (`BusinessAnalystAgent`, Section 5.7) — no skill was rewritten. This is the concrete evidence behind Objective 4 (Section 1.4).

### 5.4 Output Artifact Format

```json
{
  "schemaVersion": "artifact.v1",
  "agent": "project-analyst-agent",
  "skill": "code-qa",
  "taskId": "b3f1...",
  "createdAt": "2026-07-24T09:00:00Z",
  "result": {
    "answer": "checkout_endpoint (app.py:14) calls charge_card, calculate_total; called by nothing in the graph (likely an entry point). Related issues: #108 (open) Payment gateway timeout not retried.",
    "evidence": [
      { "claim": "checkout_endpoint dependencies", "source": "app.py:14" },
      { "claim": "Payment gateway timeout not retried", "source": "issue #108" }
    ],
    "ungrounded": []
  },
  "reviewed": false
}
```

This is the exact shape the running system produces (`Artifact<T>` wrapping a skill-specific `result`), verified against `Artifact.java`, `Evidence.java`, and `CodeQaResult.java`, not an aspirational draft.

### 5.5 External Connectors (Read-Only)

`impact-analysis` has a second input source alongside typed free text: `GitHubPrReader` fetches a public PR's title and changed-file patches over the unauthenticated GitHub REST API (`GET /repos/{owner}/{repo}/pulls/{number}` and `.../files`) and flattens them into the same free-text shape `ImpactAnalysisSkill` already parses — no new extraction logic, the connector just changes where the text comes from. This is deliberately **GET-only**: no PR comment, label, or status check is ever written back, and the result is still a `reviewed: false` draft artifact like every other skill output. Exposed at `POST /api/skills/impact-analysis/from-pr`, surfaced in the frontend as an alternate action inside the same Impact Analysis panel (Section 4.1).

This is scoped narrowly on purpose: it is the smallest step that proves a "connector" pattern (external system → same skill → same evidence/review contract) actually works, without taking on OAuth, write permissions, or an approval-to-post workflow — all of which stay future work (Chapter 15) until this read-only slice has been used and reviewed.

### 5.6 Persistence

Every `Artifact` (Section 5.4) is now saved the moment `CoordinatorService` builds it — `ArtifactPersistenceService.save()` runs as a side effect at the same seam for every skill, so no controller has its own ad-hoc persistence logic. Storage is Spring Data JPA over H2 in **file mode** (`jdbc:h2:file:./data/miniproject`, not in-memory), specifically so review state survives a backend restart — verified directly: an artifact marked reviewed, followed by a full process kill and restart, still reports `reviewed: true` afterward.

The schema is two tables, not the fully normalized five-table design (`users`, `projects`, `analysis_artifacts`, `evidence`, `review_status`) sketched during planning:

- `analysis_artifacts` — `task_id` (primary key, the same UUID already generated by `Artifact.draft`), `profile`, `agent`, `skill`, `input_text`, `result_json`, `created_at`, `reviewed`, `reviewed_at`
- `evidence` — one row per citation, `@ManyToOne` back to `analysis_artifacts`

Two deliberate simplifications, not oversights:

- **`result` is stored as one JSON blob (`result_json`), not normalized per skill.** `code-qa`, `impact-analysis`, and `test-case-gen` each have a differently-shaped result; fully normalizing all three would mean a table per skill for no query benefit this iteration actually needs. `evidence` *is* normalized, because every skill shares that shape and it is a real query ("which claims cite issue #108 across artifacts").
- **No `projects` table yet.** The MCP server currently points at one fixed target directory via `MCP_TARGET_DIR`; a `projects` table with no multi-repo feature using it would be schema for its own sake. It becomes a real table alongside Phase 4 (a real cloned repository, Chapter 10), not before.

Two new endpoints expose this, reusing the same `Artifact<T>` envelope so the frontend's existing per-skill render functions work unchanged whether an artifact just ran or was reopened from history:

| Endpoint | Purpose |
|---|---|
| `GET /api/artifacts` | List summaries (`task_id`, `profile`, `skill`, a truncated `input_text` preview, `created_at`, `reviewed`), newest first |
| `GET /api/artifacts/{taskId}` | Full artifact, reconstructed from the DB in the same shape a live skill call returns |
| `PATCH /api/artifacts/{taskId}/review` | Sets `reviewed=true` and `reviewed_at=now()` — this is what "Mark as reviewed" actually calls now; it was UI-only (a local JS flag) before this phase |

One bug this surfaced during testing: the frontend's connectivity indicator worked by POSTing a throwaway `"__ping__"` question to `/api/skills/code-qa` on every page load. Once persistence existed, that meant every page load wrote a junk row into `analysis_artifacts`. Fixed with a separate `GET /api/health` endpoint that touches no skill and persists nothing — a small, concrete example of why "every skill call is now persisted" has to be checked against every caller of a skill endpoint, not just the obviously-intentional ones.

### 5.7 Agent Layer and Handoff

`agents/*.md` described three role agents from the start; this phase turns that from markdown into code. A new `agent` package (`Agent`, `ProjectAnalystAgent`, `BusinessAnalystAgent`, `TesterAgent`, `AgentRegistry`) replaces the static `PROFILE_SKILLS` map that used to live in `CoordinatorService` — each role's allowed-skill set now lives in its own small class instead of one map literal, and adding a role is one new `@Component` implementing `Agent`, discovered automatically by Spring. Permission behavior is unchanged and was regression-tested against the old map's exact responses (unknown profile, disallowed skill) before and after the swap — this is an architectural clarification, not a behavior change.

**What this is not:** an autonomous agent that decides which skill to call from free text. `CoordinatorService` still routes deterministically — which skill runs is still determined by which REST endpoint was called, and `Agent.allowedSkills()` only answers *whether* a profile may call it, not which skill best answers a given request. Building the latter needs an LLM reading the request and choosing/sequencing tool calls, which this codebase does not wire up — every synthesizer remains rule-based by design (Chapter 8), and no Anthropic API key is configured in this environment. Autonomous skill selection stays future work (Chapter 15) until that changes; claiming it now would repeat exactly the kind of overclaim Chapter 16 commits this proposal to avoiding.

**What *is* new and real: deterministic agent-to-agent handoff.** A Project Analyst or Business Analyst's `impact-analysis` artifact, once marked `reviewed: true`, can hand off to the Tester Agent: `POST /api/artifacts/{taskId}/handoff/test-case-gen` runs `test-case-gen` against one of that artifact's affected modules and persists the result as a new artifact under the `tester` profile. Two rules make this a governed handoff rather than a shortcut around the role/skill boundary:

- The source artifact must be `impact-analysis` **and already reviewed** — an unreviewed (possibly wrong) blast radius cannot trigger downstream test generation. This is the review gate (Section 2.5) applied to agent-to-agent handoff, not just to a human reading a final answer.
- The `target` must be one of the source artifact's own `affected_modules` names (validated server-side against the stored `result_json`) — a handoff can't be pointed at an arbitrary function the impact analysis never actually found.

The frontend surfaces this as a "→ Send to Tester" button per affected-module row, which only appears once the artifact is reviewed — verified end-to-end (button absent pre-review, five buttons present post-review, clicking one lands on a real `test-case-gen` report with the role pill switched to Software Tester).

The resulting artifact records its lineage: `analysis_artifacts.parent_task_id` is set to the source artifact's `task_id` (null for every artifact created directly, not via handoff). The History view surfaces this as a small "↳ handoff from {id}…" link under a handed-off artifact's row, clicking through to the parent — so a Tester can always answer "where did this test-case-gen artifact come from" without leaving the UI.

### 5.8 Timeline Estimation (Project Analyst handoff)

A second handoff off the same reviewed `impact-analysis` artifact: `POST /api/artifacts/{taskId}/handoff/timeline-estimation` runs a rule-based formula (development/unit-testing/QA-regression/review-UAT/risk-buffer day estimates, from `affected_modules.size()`, `risk_level`, and an optional `developers`/`testers_available` assumption pair) and returns a working-day range with its full basis and assumptions listed, not just a number. Same governance as the test-case-gen handoff: source must be `impact-analysis` and already `reviewed`.

The one thing worth calling out specifically here: **this is the first skill in the codebase whose grounding depends on what else has already been handed off from the same source**, not just on the source itself. `CoordinatorService.handoffToTimelineEstimation` calls `ArtifactPersistenceService.findChildren(sourceTaskId)` (the same `parent_task_id` lineage from Section 5.7) and looks for any `test-case-gen` artifacts already handed off. If one exists, its real generated-case count grounds the QA-regression estimate and the result is tagged `confidence: high`, with an evidence citation to that specific test-case-gen artifact. If none exists yet, the estimate falls back to a rough one-case-per-affected-module assumption, explicitly labelled as such in `basis` ("no test-case-gen artifact handed off yet, this is a rough 1-per-module assumption") rather than silently presented as equally solid — confidence drops to `medium` or `low` accordingly. Verified both ways: generating a timeline estimate before and after handing off to `test-case-gen` on the same source artifact produces different `basis`, different `confidence`, and a different case count, with the QA-regression day figure changing between runs.

`developers` and `testers_available` are optional assumptions (default 1 developer, tester available) that scale the development-day estimate and add a caveat to `assumptions` when no tester is available — surfaced in the frontend as a small inline form (developer count, tester-available checkbox) next to the "Generate Timeline Estimate" button, which only appears once the source artifact is reviewed, same visibility rule as the test-case-gen handoff button.

### 5.9 External Write-Back Connectors (Bitbucket, Jira) — Wired and Live-Verified, Both Sides

A `com.miniproject.backend.integrations` package provides a `BitbucketConnector` (comments on a Bitbucket PR via the REST API, Basic Auth with an app password), a `JiraConnector` (creates a Jira issue via the REST API), and an `ExternalHandoffService` that orchestrates both behind the same governance pattern as the internal handoffs in Sections 5.7/5.8: the source artifact must already be `reviewed`, and every call defaults to **dry-run** unless the caller explicitly passes `dry_run: false` — a dry-run returns a "ready to publish" result without making any external HTTP call. Results are persisted to a third table, `external_handoffs` (`source_task_id`, `destination`, `status`, `external_key`/`external_url`, `dry_run`, `created_at`), giving an audit trail independent of whether the call was ever un-dry-run.

This is now fully wired end-to-end, not just present as backend code:

- `POST /api/artifacts/{taskId}/external-handoff` and `GET /api/artifacts/{taskId}/external-handoffs` expose `ExternalHandoffService` — read-verified directly against the running backend.
- The frontend has a "Publish" panel (visible once an artifact is `reviewed`, same visibility rule as the other handoffs): a summary field, a Bitbucket-PR-URL field, a dry-run checkbox (checked by default), and separate "Create Jira Issue" / "Comment Bitbucket PR" actions, with results and history rendered inline.
- **Jira is live-verified, not just dry-run tested.** `JiraConnector` handles the routing that Atlassian's newer scoped API tokens require: `integrations.jira.auth-mode: scoped` + a `cloud-id` route requests through `https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3/issue` instead of hitting the site URL directly, which classic-token Basic Auth would use. This was confirmed against the real API, not assumed: with `dry_run: false`, a reviewed `impact-analysis` artifact ("Add automatic retry to charge_card when the payment gateway times out") created a real Jira issue, **KAN-2**, in the author's own Jira Cloud project (`ng-ling-ling.atlassian.net`), and `GET /api/artifacts/{taskId}/external-handoffs` correctly returns that record (`status: CREATED`, `external_key: KAN-2`, a working `external_url`, `dry_run: false`) from the persisted audit trail.
- **Bitbucket is also live-verified, independently of Jira.** The same audit trail contains a real (`dry_run: false`) comment: `status: COMMENTED`, `external_key: 828611336`, `external_url: https://bitbucket.org/miniproject2026/internproject_frontend/pull-requests/1/_/diff#comment-828611336`, posted from a reviewed artifact against a real PR (`miniproject2026/internproject_frontend#1`), alongside three preceding dry-run records for the same PR. A first attempt to verify this by scanning every artifact's `external-handoffs` came back empty and was reported as "not found" — re-querying the specific source artifact directly returned the record, so the first scan was a false negative (most likely an incomplete artifact list at the time it ran), not evidence the handoff hadn't happened. Corrected here rather than left standing, per the same verify-before-claiming standard this proposal has applied throughout.
- Two mocked unit tests (`ExternalHandoffServiceTest`) additionally verify the reviewed-gate rejection and a dry-run handoff being recorded in isolation from any real network call; the whole backend (9 tests total) compiles and passes with this package included.

One thing still worth being precise about, so this section doesn't over-claim in the other direction:

- **Bitbucket is still write-only.** `BitbucketConnector` only posts a comment; unlike `GitHubPrReader` (Section 5.5), there is no Bitbucket equivalent that fetches a PR's diff to feed `impact-analysis`. A repository hosted on Bitbucket still cannot use the PR-as-input workflow — only GitHub can, today.

Much of this section's code (the connectors, the service, the entity/repository, the initial tests) was not authored as part of this proposal's tracked phase sequence (Chapter 10) — it predates and runs alongside the work in Sections 5.5–5.8, and the endpoint/frontend wiring described above was completed and self-tested by the author outside the increment-by-increment loop this proposal otherwise documents (Chapter 8). It is included here, with what's verified stated precisely, because a supervisor reading this proposal against the running repository should be able to trust every claim in it equally, regardless of which sitting produced the code.

### 5.10 React Frontend — Now the Primary UI

The frontend row in Section 5's architecture table has read "HTML/JS prototype today; React is the stated target" throughout this proposal, with a full React build listed as Planned (Phase 5, Chapter 10). That is no longer accurate: `frontend/src/main.jsx` is a working React app (Vite 6, React 18, 833 lines of JSX plus a 702-line stylesheet), and `frontend/README.md` now states it, not the vanilla prototype, is the primary frontend.

What was verified, and how:

- **It runs.** `npm run dev` serves the app; a request to its root returned `HTTP 200`. A live instance was already running on port 5173 before this check — evidence the app is in active use, not merely built and left untried.
- **It covers the same backend surface this proposal documents**, confirmed by reading the source rather than trusting the README: `/api/skills/code-qa`, `/api/skills/impact-analysis` (including `/from-pr`, Section 5.5), `/api/skills/test-case-gen`, `/api/artifacts` history, `/api/artifacts/{id}/review`, `/api/artifacts/{id}/handoff/test-case-gen` (5.7), `/api/artifacts/{id}/handoff/timeline-estimation` (5.8), and `/api/artifacts/{id}/external-handoff` (5.9) all appear as real fetch calls in `main.jsx`, not a subset.
- **What was not independently re-verified here:** a full click-through browser test of the React app the way the vanilla prototype's flows were verified earlier in this project (Playwright, screenshots) was not repeated for this app in this sitting — its correctness rests on the endpoint coverage check above, the fact it's already running, and the author's own screenshot of a working Publish panel (Section 5.9), not on a fresh independent UI test.

The vanilla HTML/JS prototype (`frontend/prototype/code-qa.html`) remains in the repository as a reference/fallback per `frontend/README.md`, and every claim about it earlier in this proposal (Section 4.1, Chapter 6, Chapter 11) remains accurate as a description of that file — it has not been deleted or replaced, only superseded as the primary UI.

---

## 6. Functional Design (Workflow View)

Skills are already decoupled from roles (Section 5.3); this chapter groups them by the recurring engineering task they serve, purely as a presentation and planning device — **it does not introduce a new architectural layer**, only a label for grouping skills that already exist independently. Per Section 4.1, each workflow below is a distinct, form-driven action in the UI, not a turn in an open-ended chat.

### Workflow: Code & Issue Understanding

```
Free-text question
        │
        ▼
Extract candidate identifiers (regex, no LLM)
        │
        ▼
get_endpoint_info (per candidate) + search_issues (whole question)
        │
        ▼
Evidence-backed answer, or an explicit "ungrounded" note if nothing resolved
```
Status: **Implemented** (`CodeQaSkill`, `RuleBasedAnswerSynthesizer`).

### Workflow: Change Impact Analysis

```
Change request text (own structured form field, not a chat prompt)
        │
        ▼
Extract candidate identifiers → trace_impact per candidate
        │
        ▼
trace_impact does the 1–2 hop blast-radius expansion server-side
(BFS over calls/called_by in the graph, both directions)
        │
        ▼
search_issues once over the whole change request (blast radius grounding)
        │
        ▼
Aggregate: affected-module list, related historical issues, rule-based
risk tag (low / medium / elevated by issue count — no LLM)
```
Status: **Implemented** (`ImpactAnalysisSkill`, `RuleBasedImpactAnalysisSynthesizer`, backed by a new `trace_impact` MCP tool that does the multi-hop BFS server-side in `mcp_server/graph.py`, rather than the Java layer calling `get_endpoint_info` repeatedly). Exposed at `POST /api/skills/impact-analysis` and surfaced in the frontend as its own workflow tab (Section 4.1), not a chat turn.

### Workflow: Test Planning

```
Function/endpoint target (typed, or handed off from Impact Analysis output)
        │
        ▼
get_endpoint_info(target) → resolve signature-level facts; stop if unresolved
        │
        ▼
get_endpoint_info per call/caller (exact file:line citations, not just names)
        │
        ▼
get_test_coverage(target) → existing test_* functions already covering it
        │
        ▼
Template positive (per call), edge (per caller), and negative (per target)
cases — every case's rationale cites a real graph edge
        │
        ▼
search_issues(target) → template a regression check per related historical
issue (e.g. "verify {issue title} does not recur — see issue #{id}")
```
Status: **Implemented** (`TestCaseGenSkill`, `RuleBasedTestCaseGenSynthesizer`, backed by a new `get_test_coverage` MCP tool — reuses the existing call-graph data rather than a separate coverage-scanning pass, since a `test_*` function is just another graph node whose `calls` list already includes what it tests). Exposed at `POST /api/skills/test-case-gen`, restricted to the Tester profile, surfaced as its own workflow tab.

---

## 7. AI Technologies Applied

| Technology | Application in This Project | Market Context |
|---|---|---|
| Model Context Protocol (MCP) | Python MCP server exposes graph functions as standardised tools | Open protocol from Anthropic, adopted broadly across AI tooling in 2026 |
| Knowledge Graph | Code, functions, files, and issues as graph nodes with typed edges; dependency tracing instead of keyword matching | Validated as a real market direction by Greptile, CodeGraph, Understand Anything (Chapter 3) |
| Context Engineering | Role profiles define persona, permitted skills, and default view | Structured, repeatable context architecture rather than ad-hoc prompting |
| Human-in-the-Loop | Every output defaults to `reviewed: false` | Standard governance pattern for responsible AI deployment |
| Evidence Grounding | Every claim carries a typed `Evidence` citation; unresolved claims are reported, not hidden | Directly addresses hallucination risk |
| Agentic Skill Routing | Harness routes a request to the skills a role is permitted to call, backed by a real `Agent`/`AgentRegistry` layer (Section 5.7) plus deterministic agent-to-agent handoff (reviewed impact-analysis → test-case-gen) | Reflects the shift from single-prompt tools to structured, multi-skill systems |

---

## 8. Development Methodology

Development followed an **iterative, evidence-first approach** rather than a fixed waterfall plan: each iteration added one verifiable capability (graph → MCP tools → skill → role gate → review gate) and was checked against the running system before the next was added, rather than designing the full system up front and building it in one pass. This is also why the project's rule-based synthesizer was built and proven before any LLM integration was attempted — correctness of the deterministic path was treated as the priority over adding generative capability early.

```
Plan (smallest next capability)
        │
        ▼
Implement against existing tools where possible
        │
        ▼
Verify against the real running system (not assumption)
        │
        ▼
Reconcile documentation/proposal claims against what was actually built
        │
        ▼
Repeat
```

---

## 9. Project Scope

### 9.1 Vision vs. Delivered Scope

The end state this architecture is built toward is a **Change Impact Intelligence** workflow: a change request comes in, and the system surfaces every affected module, every historical incident in that blast radius, a risk/effort signal for the Project Analyst or Business Analyst, and a suggested regression scope for the Tester. The scope below is the **first slice** of that vision — the graph, the MCP tool layer, one fully working skill, three configured role profiles, and the review-gate data contract every future skill reuses.

### 9.2 Delivered in This Iteration

- Python MCP server with code parsing and call-graph construction
- Four MCP tools (`get_endpoint_info`, `search_issues`, `trace_impact`, `get_test_coverage`)
- Issue data ingestion and linking to graph nodes
- Spring Boot backend with a real MCP client, an `Agent`/`AgentRegistry` layer backing role routing across three profiles (Section 5.7), and four implemented skills (`code-qa`, `impact-analysis`, `test-case-gen`, `timeline-estimation`)
- A read-only `GitHubPrReader` connector: `impact-analysis` can now run against a public GitHub PR URL instead of typed text, with no write-back to GitHub (Section 5.5)
- Persistent storage: every artifact and its review state survive a backend restart (Spring Data JPA + H2, file mode — Section 5.6), with a history view to browse and reopen past artifacts, including a visible parent/child lineage link for handed-off artifacts
- Two deterministic agent-to-agent handoffs from a reviewed `impact-analysis` artifact: to the Tester Agent's `test-case-gen` (Section 5.7), and to the Project Analyst's own `timeline-estimation` (Section 5.8) — the latter grounds its QA-regression estimate in real generated case counts when a `test-case-gen` handoff already exists for the same source
- A React (Vite) frontend, now the primary UI, calling the real backend end-to-end for all four skills plus every handoff (workflow-per-skill layout, role → analysis choice → report, Section 4.1); the earlier vanilla HTML/JS prototype remains as a reference/fallback (Section 5.10)
- Evidence trail on every output; human review gate (`reviewed: false` by default)
- Project documentation (README, architecture rationale, this proposal)
- A Bitbucket/Jira write-back connector layer (`BitbucketConnector`, `JiraConnector`, `ExternalHandoffService`) with the same reviewed-gate + dry-run-by-default safety pattern, wired to `POST /api/artifacts/{taskId}/external-handoff` and a frontend Publish panel — **both** Jira issue creation (real ticket KAN-2) and Bitbucket PR comments (real comment 828611336) are live-verified with persisted audit records (Section 5.9)

### 9.3 Not in This Iteration

**Next in line — direct extensions of the same graph and Artifact schema, no new architecture required:**

- `weekly-report` skill
- A `projects` table + multi-repo support, once Phase 4 replaces the single fixed `MCP_TARGET_DIR` with a real project-selection flow

**Further out — larger, separate efforts:**

- Autonomous LLM-based skill/tool selection — an agent reading free text and deciding which skill(s) to call, rather than the deterministic per-endpoint routing this iteration uses (Section 5.7); requires an LLM-based synthesizer and an Anthropic API key this environment does not currently have configured
- A Bitbucket *read* connector (fetch a PR's diff to feed `impact-analysis`, mirroring `GitHubPrReader`) — today only GitHub PRs can be used as impact-analysis input; Bitbucket's existing connector is write-only (Section 5.9)
- Multiple programming language support
- Enterprise authentication and access control (current CORS policy is intentionally open for local dev only)
- Production deployment
- A real cloned public repository in place of the hand-authored sample target

---

## 10. Implementation Plan

> Status note: Weeks 1–2 are complete and verified end-to-end (Python MCP server, Spring Boot MCP client, `code-qa` skill, artifact envelope with review gate, three role profiles). Phases 3 through 3.9 (`impact-analysis`, `test-case-gen`, `timeline-estimation`, the read-only GitHub PR connector, persistence, the agent/handoff layer, Bitbucket/Jira write-back, and the React frontend) are now also complete — both the Jira and Bitbucket write-back paths are live-verified against real accounts (ticket KAN-2, PR comment 828611336), not just tested in isolation. The plan below reflects remaining work.

| Phase | Focus | Status |
|---|---|---|
| Phase 1 | Knowledge Graph + MCP Server | **Complete** — running against a hand-authored sample target |
| Phase 2 | AI Harness + Backend + Role Profiles | **Complete** for the deterministic path; three roles configured |
| Phase 3 | `impact-analysis` + `test-case-gen` skills, read-only GitHub PR connector | **Complete** — verified end-to-end including frontend workflow tabs |
| Phase 3.5 | Persistent storage (Spring Data JPA + H2) for artifacts and review state | **Complete** — verified across a full backend restart |
| Phase 3.6 | Formal `Agent`/`AgentRegistry` layer + deterministic reviewed-impact-analysis → test-case-gen handoff | **Complete** — verified end-to-end, including the pre-review rejection and invalid-target rejection paths |
| Phase 3.7 | `timeline-estimation` skill + handoff, grounded further by any linked test-case-gen artifact | **Complete** — verified both ungrounded and grounded (post test-case-gen handoff) paths |
| Phase 3.8 | External write-back connectors (Bitbucket PR comment, Jira issue creation) | **Complete** — both Jira (KAN-2) and Bitbucket (comment 828611336) live-verified with persisted audit records (Section 5.9) |
| Phase 3.9 | React (Vite) frontend | **Complete** — running, covers every endpoint this proposal documents, now the primary UI (Section 5.10) |
| Phase 4 | Real target repository + live issue ingestion | Planned |
| Phase 5 | Demo preparation + documentation | Remaining scope narrowed to demo prep now that the frontend rebuild (formerly this phase) is done |

### Phase 3–5 Detailed Breakdown

```
Phase 3 — impact-analysis skill (done):
  - New trace_impact MCP tool (mcp_server/graph.py): BFS over calls/
    called_by from a candidate entry point, out to max_hops in each
    direction, tagged with hop count and relation
  - ImpactAnalysisSkill extracts candidates from a change-request-length
    input (same regex/stopword approach as CodeQaSkill) and calls
    trace_impact per candidate + search_issues once over the request
  - RuleBasedImpactAnalysisSynthesizer aggregates the blast radius,
    dedupes affected modules, tags risk level from issue count, and
    estimates rough effort (S/M/L) from affected-module count — no LLM
  - New ImpactAnalysisResult record + ImpactAnalysisSynthesizer interface
    (mirrors AnswerSynthesizer), reusing Artifact/Evidence
  - Exposed at POST /api/skills/impact-analysis; frontend gained a
    skill-tab selector and a dedicated report view (Section 4.1)
  - GitHubPrReader (read-only): fetches a public PR's title + changed-file
    patches over the unauthenticated GitHub REST API and flattens them into
    the same free-text shape impact-analysis already parses — no write-back
    (Section 5.5). Exposed at POST /api/skills/impact-analysis/from-pr
  - test-case-gen skill: get_test_coverage MCP tool (reuses the existing
    call-graph data — a test_* function is just another node whose calls
    list already says what it tests) + TestCaseGenSkill/Synthesizer
    templating positive/negative/edge cases and a regression checklist,
    every case citing a real file:line. Exposed at POST /api/skills/test-case-gen,
    restricted to the Tester profile

Phase 3.5 — persistent storage (done):
  - Spring Data JPA + H2 (file-mode, not in-memory, so data survives a
    restart) persisting every Artifact and its reviewed/reviewed_at state
    (AnalysisArtifactEntity + EvidenceEntity, Section 5.6)
  - "Mark as reviewed" is now a real PATCH /api/artifacts/{id}/review call,
    not a UI-only flag
  - An analysis-history view (GET /api/artifacts, GET /api/artifacts/{id})
    listing and reopening past artifacts, reusing the existing per-skill
    report renderers unchanged
  - Fixed a real bug this surfaced: the frontend's connectivity check used
    to POST a throwaway question to /api/skills/code-qa, which started
    persisting junk rows once every skill call was saved — replaced with a
    separate GET /api/health that persists nothing

Phase 3.6 — agent layer + handoff (done):
  - New agent package: Agent interface, ProjectAnalystAgent,
    BusinessAnalystAgent, TesterAgent, AgentRegistry — replaces the static
    PROFILE_SKILLS map in CoordinatorService with one @Component per role
    (Section 5.7). Permission behaviour regression-tested unchanged.
  - Added a `name` field to ImpactAnalysisResult.AffectedModule so a
    downstream handoff has a clean grounded key, not text scraped from a
    human-readable reason string
  - CoordinatorService.handoffToTestCaseGen: reviewed impact-analysis
    artifact -> test-case-gen on one of its affected modules, gated on
    source.skill == impact-analysis, source.reviewed == true, and
    target being one of the source's own affected_modules names
  - POST /api/artifacts/{taskId}/handoff/test-case-gen; frontend shows a
    "-> Send to Tester" button per affected-module row, only once reviewed

Phase 3.8 — external write-back connectors (complete for Jira):
  - BitbucketConnector (comment on a PR), JiraConnector (create an issue,
    including scoped-API-token gateway routing via cloud-id), External
    HandoffService (orchestrates both behind the reviewed-gate + dry-run-
    by-default pattern), ExternalHandoffEntity/Repository (audit trail
    table, external_handoffs) — not written as part of this proposal's
    increment-by-increment loop (Chapter 8), completed and self-tested
    by the author directly
  - POST /api/artifacts/{taskId}/external-handoff + GET .../external-
    handoffs wired in ArtifactHistoryController; frontend Publish panel
    (summary field, Bitbucket PR URL field, dry-run checkbox default on,
    separate Create-Jira-Issue / Comment-Bitbucket-PR actions)
  - Live-verified for Jira: dry-run tested first, then a real (dry_run:
    false) call against a reviewed impact-analysis artifact created Jira
    issue KAN-2 in the author's real Jira Cloud project; confirmed via
    GET .../external-handoffs returning the persisted record with the
    real external_key/external_url — read-verified independently, not
    just trusted from the UI screenshot
  - Live-verified for Bitbucket the same way: a real (dry_run: false)
    comment (external_key 828611336) on miniproject2026/internproject_
    frontend#1, alongside three preceding dry-run records for the same
    PR — an initial verification scan missed this record and reported
    a false negative; re-querying the specific source artifact directly
    found it, and the false negative was corrected rather than left in
    the record
  - All 9 backend tests still pass with this wired in, including the 2
    ExternalHandoffServiceTest cases (reviewed-gate rejection, dry-run
    handoff recorded)
  - Still true: Bitbucket has no read path (can't fetch a PR diff the
    way GitHubPrReader does for GitHub) — write-back works both ways,
    PR-as-input still only works for GitHub

Phase 3.9 — React frontend (complete, found already in progress):
  - frontend/src/main.jsx (Vite 6, React 18) covers code-qa, impact-
    analysis (incl. from-pr), test-case-gen, artifact history/review,
    and both handoffs (test-case-gen, timeline-estimation) plus the
    external-handoff (Jira/Bitbucket) panel — verified by reading the
    source against the same endpoint list this proposal documents
  - Verified running: `npm run dev` serves HTTP 200; a live instance
    was already active on port 5173 before this was checked
  - Not independently re-verified: a full Playwright click-through of
    the React app itself (the vanilla prototype got that treatment
    earlier in this project; this app's correctness rests on endpoint
    coverage + it already being live-used, not a fresh UI test)
  - frontend/prototype/code-qa.html retained as reference/fallback,
    per frontend/README.md — not deleted, superseded as primary only

Phase 4 — real target repository:
  - Select and clone a real public demo repository
  - Point the MCP server's parser at it; fix parser gaps found on real code
  - Replace the hand-authored issues.json with real issue ingestion

Phase 5 — demo + docs:
  - Prepare demo scenarios matched to what the graph can actually answer
  - Update README, architecture docs, and this proposal to match
```

---

## 11. Demonstration Scenario

**Input:** "What does checkout_endpoint depend on, and are there known issues with it?"

**System Process:**

1. User selects a role (Project Analyst, Business Analyst, or Tester).
2. System identifies the `code-qa` skill as appropriate for that role.
3. System extracts identifier candidates from the question and calls `get_endpoint_info` for each candidate found in the graph.
4. The MCP server returns `checkout_endpoint`'s file, line, the functions it calls, and the functions that call it.
5. System calls `search_issues` with the question text.
6. System assembles a structured response with evidence references.

**Actual output (verified against the running system):**

```
checkout_endpoint (app.py:14) calls charge_card, calculate_total;
called by nothing in the graph (likely an entry point).

Related issues:
#108 (open) Payment gateway timeout not retried — charge_card does not
retry on gateway timeout, checkout_endpoint surfaces a raw 500 to the
customer.

Evidence:
- checkout_endpoint dependencies → app.py:14
- Payment gateway timeout not retried → issue #108

Review Status: UNREVIEWED
```

A live client for this exact flow exists at `frontend/prototype/code-qa.html`, wired to `POST /api/skills/code-qa` on the real Spring Boot backend — this is not a mockup of the response shape, it is the response shape.

---

## 12. Evaluation Plan

| Criterion | Measure |
|---|---|
| Functional completeness | End-to-end flow runs: question/CR input → MCP tool call → evidence-backed response → review gate |
| Evidence validity | Conclusions reference actual source files, methods, and issues in the target repository |
| Response correctness | Answers accurately reflect what `get_endpoint_info`/`search_issues` return — no unsupported claims |
| Technology integration | MCP, knowledge graph, context engineering, human-in-the-loop, and evidence grounding are all demonstrably applied, not just claimed |
| Role reusability | Adding a new role (demonstrated with Business Analyst) requires configuration only, no skill rewrite |
| Human review acceptance | Every output is inspectable and gate-able; nothing is auto-confirmed |
| Code quality | Clean separation between MCP server, backend harness, and frontend; modular skill and role definitions |

---

## 13. Risk Analysis

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| MCP subprocess fails to start or crashes | High — backend unusable | Low | `McpToolClient` surfaces a clear `McpToolException`; documented restart via `mvn spring-boot:run` |
| Function-name collisions in the graph (documented MVP limitation) | Medium — wrong attribution | Medium on large/multi-module repos | Explicitly documented limitation (`mcp-server/README.md`); demo target kept small/single-module this iteration |
| Future LLM-based synthesizer hallucination, once introduced | High — false confidence in output | N/A yet — not built | Human review gate is structural (`reviewed: false` default), independent of which synthesizer produced the answer |
| Issue tracker data incomplete or stale | Medium — missed historical risk signal | Medium | System reports an explicit empty/`ungrounded` result rather than guessing |
| CORS currently open (`origins = "*"`) | Medium — security, if exposed beyond local dev | Low while local-only | Documented as dev-only; enterprise auth is explicit future work (Section 9.3) before any real deployment |
| Scope creep from evolving proposal discussion | Medium — missed delivery target | Medium | Section 9 explicitly splits delivered scope from vision/future work; every new idea raised in review is routed to Chapter 15 unless it reuses existing plumbing |
| Real external credentials (Bitbucket app password, Jira API token) in local config, actively used for a live write-back path (Section 5.9) | High if leaked — third-party account compromise; medium for accidental real writes now that the path is live | Low | Credentials stored only in `application-local.yml`, gitignored and confirmed never committed; every write-back call defaults to dry-run unless `dry_run: false` is explicit, and the source artifact must already be `reviewed` — the same two gates that made the pre-wired state safe still apply now that it's live |

---

## 14. Known Limitations

- Static code analysis may not detect runtime dependencies, reflection-based calls, or configuration-driven relationships.
- The current version supports only one programming language (Python) for the target repository, and call resolution is syntactic, not type-resolved.
- Function names are assumed unique across the target — correct for the current small sample target, not yet safe for large multi-module projects.
- Issue-to-code linking depends on the quality of references in the issue tracker.
- The current answer synthesizer is deterministic and rule-based, not LLM-generated; this is a scope decision (Chapter 8), not an oversight, but it does mean the system cannot yet handle open-ended phrasing outside its extraction patterns.
- The primary frontend is now the React (Vite) app (Section 5.10), matching the original technology stack; the earlier vanilla HTML/JS prototype is retained as a reference/fallback, not a claim of the current primary UI.
- As shown in Section 3.2, this system serves a narrow, specific slice of each target role's job — it is not a claim of full job automation for any of the three roles.

---

## 15. Future Work

### Short-term (next iteration)

- Implement `weekly-report`
- Replace the hand-authored sample target with a real cloned public repository and live issue ingestion
- Independently live-verify the Bitbucket comment path (Section 5.9) the same way the Jira path already has been — a real, non-dry-run comment on a real Bitbucket PR

### Mid-term

- Introduce an LLM-based `AnswerSynthesizer` as an alternative to the rule-based one, behind the same interface
- A Bitbucket *read* connector mirroring `GitHubPrReader` (Section 5.5) — fetch a PR's diff to feed `impact-analysis`; today the existing Bitbucket connector (Section 5.9) is write-only
- Add a lightweight, templated "stakeholder report" export from an `impact-analysis` artifact, to give partial reach into report-writing/communication needs without building a full report generator
- Multi-language parsing support beyond Python

### Long-term

- **Bridge to Hermes (the author's separate production incident-response project), staged and explicitly gated on this mini-project standing on its own first.** Hermes already applies the same "no confirmed finding without evidence, no action before human confirmation" principle to root-cause analysis (production issues → evidence from logs/queries/endpoints → root cause → solution, each stage revisable by a human before advancing). The two systems' data models already line up — Hermes's `Evidence` concept and this project's `Evidence(claim, source)`; Hermes's human-revision loop and this project's `reviewed: false` gate; Hermes's RCA→"New Project" handoff and this project's existing `CoordinatorService` handoff pattern (Sections 5.7–5.9). The smallest bridge, once justified, is an inbound adapter mirroring `GitHubPrReader` (Section 5.5) — e.g. `POST /api/skills/impact-analysis/from-rca` — converting a confirmed Hermes root-cause finding into the same free-text shape `ImpactAnalysisSkill` already parses, reusing existing extraction rather than building new architecture. Not started, and deliberately not prioritised, until the skills/agent/handoff layer in this proposal has proven itself independently — integrating two unproven systems compounds risk instead of reducing it.
- Generalise the graph → MCP tools → skills → role profiles → review-gate architecture beyond source code to other evidence-driven domains more broadly, of which the Hermes bridge above is the first concrete instance
- Enterprise authentication, multi-tenant deployment
- Evaluate additional role profiles (e.g. Developer, Project Manager) only if real demand justifies it, reusing the existing skill layer rather than rewriting it

---

## 16. Conclusion

This proposal presents an evidence-driven, graph-based platform for Software Delivery Analysts — Project Analysts, Business Analysts, and Software Testers — built around a project knowledge graph that fuses code structure with historical issue data, exposed through standardised MCP tools, and gated behind mandatory human review. The foundation (graph, MCP tool layer, four implemented skills, persistence with agent-to-agent handoff and lineage tracking, three configured role profiles, a React frontend, and the review-gate data contract) is built and verified end-to-end, not aspirational — extending, per Section 5.9, to a Bitbucket/Jira write-back path with **both** sides live-verified against real accounts (Jira ticket KAN-2, Bitbucket comment 828611336), not merely unit-tested in isolation. The project is deliberately honest about what remains future work (Chapter 15) and about the specific, narrow slice of each target role's job it actually serves (Section 3.2) — a choice made to keep this proposal's claims defensible against direct scrutiny, rather than to make it sound larger than it is.

---

## References

- Model Context Protocol specification — Anthropic, https://modelcontextprotocol.io/
- Greptile — AI code review platform, https://www.greptile.com/
- Greptile — graph-based codebase context documentation, https://www.greptile.com/docs/how-greptile-works/graph-based-codebase-context
- CodeGraph — open-source code knowledge graph, https://github.com/colbymchenry/codegraph
- Understand Anything — Claude Code knowledge-graph plugin, https://github.com/Egonex-AI/Understand-Anything

---

## Appendices

### Appendix A — MCP Tool Schemas

```
get_endpoint_info(name: str) -> dict
  { "found": bool, "name": str, "file": str, "line": int,
    "calls": [str], "called_by": [str] }

search_issues(query: str) -> dict
  { "query": str, "matches": [ { "id": int, "title": str,
    "state": str, "body": str, "files": [str] } ], "count": int }

trace_impact(name: str, max_hops: int = 2) -> dict
  { "found": bool, "name": str, "file": str, "line": int,
    "affected": [ { "name": str, "file": str, "line": int,
      "hops": int, "relation": "calls" | "called_by" } ] }

get_test_coverage(name: str) -> dict
  { "found": bool, "name": str,
    "covered_by": [ { "test": str, "file": str, "line": int } ] }
```

### Appendix B — Artifact Schema (full)

See Section 5.4 for the verified JSON shape (`Artifact<T>` / `Evidence` / `CodeQaResult`).

### Appendix C — Reusing the MCP Server Outside This Platform

Because `mcp-server/` is a standard stdio MCP server, any MCP-speaking client can call it directly (e.g. by adding it to Claude Code's or Cursor's `.mcp.json`). Doing so bypasses the role-gating and review-gate governance described in Chapter 5 — those are properties of this platform's harness, not of the graph or tools themselves. This is a deliberate, understood trade-off, not an oversight: it demonstrates the tools are genuinely reusable (Objective 2), while making clear that reusability and governance are separate claims.

### Appendix D — Role Profile Documents

Full persona documents: `profiles/project-analyst.md`, `profiles/business-analyst.md`, `profiles/tester.md`.

### Appendix E — Vanilla Prototype (Fallback)

`frontend/prototype/code-qa.html` — a vanilla HTML/JS client wired to `POST http://localhost:8080/api/skills/code-qa`, demonstrating the full role → ask → evidence → review flow against the live backend described in Chapter 11. Retained as a reference/fallback per `frontend/README.md`; the React app (Appendix F) is the primary UI as of Section 5.10.

### Appendix F — Primary Frontend (React)

`frontend/src/main.jsx` — the primary UI as of Section 5.10, run with `npm run dev` from `frontend/` (Vite, default `http://127.0.0.1:5173`, configurable via `VITE_API_BASE`). Covers every skill and handoff endpoint documented in this proposal, including the Section 5.9 external-handoff (Jira/Bitbucket) panel.
