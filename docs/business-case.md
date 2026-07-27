# Proposal

| Field | Detail |
|---|---|
| Project Title | Evidence-Driven AI Software Change Intelligence Platform |
| Member Name | Ng Ling Ling |
| Date | 6 July 2026 |

## Project Title: Evidence-Driven AI Software Change Intelligence Platform

## Business Case with Business Rules

Software delivery teams — Project Analysts, Business Analysts, and Software Testers — routinely spend significant time manually cross-referencing source code, issue trackers, and documentation to understand the scope and risk of a proposed change. This platform addresses this by providing a graph-based, evidence-grounded analysis tool that fuses code structure with historical issue data and gates every output behind mandatory human review. The business rules governing the platform include:

1. **Evidence Grounding**: Every AI-generated claim must cite a traceable source (file, line number, or issue ID). Unresolved claims are explicitly reported rather than silently omitted.
2. **Human Review Gate**: All outputs are marked `reviewed: false` by default. No output may be treated as final or acted upon until a human analyst confirms it.
3. **Role-Based Skill Access**: Project Analysts, Business Analysts, and Software Testers each access only the skills and data views appropriate to their role — enforced by configuration, not by duplicating code.
4. **Graph Integrity**: The graph is rebuilt from scratch on each MCP server restart, so it never silently drifts from the source and issue files it points at. Historical issues — including closed ones — are deliberately retained, not purged, since a resolved incident remains valid evidence for "has this broken before"; purging closed issues would delete the exact historical signal this platform exists to surface.

## Problem Statement

Software delivery analysts lack a dedicated tool for answering relationship and change-history questions about codebases they did not author. Existing AI coding assistants (GitHub Copilot, Cursor, Claude Code) are built for developers writing code — they answer "what does this function do" but not "what else breaks if I change this" or "has this component caused production incidents before." Without evidence-backed, role-appropriate answers, analysts must spend days manually tracing dependencies and searching issue logs before they can make a defensible change-scope or test-scope decision. AI tools that produce confident-sounding answers without citations are not usable in professional delivery contexts where claims must be auditable.

## Objectives

The objectives of this project are as follows:

1. Build a project knowledge graph that represents code structure and historical issue data as a single connected graph, enabling dependency and incident-history queries from one source.
2. Expose the graph as standardised Model Context Protocol (MCP) tools, so the same graph layer is reusable by this platform, by other MCP-speaking clients, and by future skills without re-implementation.
3. Implement one complete, evidence-grounded skill (`code-qa`) end-to-end — including identifier extraction, MCP tool calls, evidence assembly, and a human-review gate — as proof that the architecture is functional before additional skills are built.
4. Demonstrate that three analyst roles (Project Analyst, Business Analyst, Software Tester) are served by the same graph, tools, and skill layer through role-profile configuration only.
5. Reduce the time analysts spend manually cross-referencing code and issue trackers before a change can be scoped, validated, or tested.

## Scope

The scope of this project includes:

1. Designing and developing a Python MCP server that parses source code, constructs a function-level call graph, ingests issue data, and exposes two MCP tools: `get_endpoint_info` and `search_issues`.
2. Implementing a Java Spring Boot backend that acts as an MCP client, enforces role-based skill routing across three analyst profiles, assembles evidence-backed output artifacts, and manages the human-review gate (`reviewed: false` by default).
3. Building a frontend prototype (HTML/JS) that supports role selection, free-text question input, structured evidence display, and review-status controls — wired to the live backend.
4. Implementing one fully functional skill (`code-qa`) and designing two further skills (`impact-analysis`, `test-case-gen`) for the next iteration.
5. Ensuring every output artifact carries typed evidence citations and an explicit `ungrounded` field for claims the graph could not resolve.
6. Conducting end-to-end verification of the full flow — question input to evidence-backed, reviewable output — using a hand-authored sample target repository.

The project will focus on the evidence-grounding and human-review principles as non-negotiable architectural constraints, not optional features.

## Project Framework Table

| Background (current situation) | Problem (abstraction) | Objective (abstraction) | CRUD (pattern recognition) | New Scenario with Business Rules |
|---|---|---|---|---|
| Software delivery analysts — Project Analysts, Business Analysts, and Software Testers — spend days manually cross-referencing source code, issue trackers, and documentation before they can scope a change, assess risk, or plan regression tests. Existing AI tools (Copilot, Cursor) are designed for developers writing code, not for analysts deciding whether a change is safe. | Analysts lack a tool that answers relationship and history questions — "what breaks if I change this?", "has this broken before?" — with traceable, cited evidence. AI outputs without citations cannot be acted on in a professional delivery context. | 1. Build a project knowledge graph fusing code structure and issue history.<br>2. Expose the graph as MCP tools reusable across clients.<br>3. Implement an evidence-grounded skill layer with mandatory human review.<br>4. Serve three analyst roles through configuration, not re-implementation. | **Create**: Ingest source code and issues into the project knowledge graph.<br>**Read**: Query graph for dependencies, call chains, and related issues.<br>**Update**: Not yet automated — refreshing the graph today means restarting the MCP server, which fully re-parses the target directory from scratch; incremental refresh is future work, not implemented in this iteration.<br>**Delete**: Not implemented, and not planned as "purge closed issues" — closed issues are intentionally kept as historical evidence. The only deletion that occurs is stale function nodes naturally disappearing when the graph is rebuilt from scratch on restart. | 1. **Evidence Grounding**: Every AI claim cites a source file, line, or issue number — ungrounded claims are reported, not hidden.<br>2. **Human Review Gate**: All outputs default to `reviewed: false`; no output is treated as final without human confirmation.<br>3. **Role-Based Access**: Project Analyst, Business Analyst, and Tester each access only the skills permitted for their role.<br>4. **Graph Integrity**: Code and issue data are re-parsed from scratch on each server restart; historical (including closed) issues are retained by design, never purged. |
