# Software Analyst Workflow Assistant Proposal

| Field | Detail |
|---|---|
| Prepared by | Ling |
| Type | Internship Mini Project |
| Current Direction | Software Analyst workflow assistant |

## Summary

This project builds a workflow assistant for a Software Analyst's daily work. It does not recreate Claude Skills. Instead, it coordinates existing AI-adjacent capabilities such as skills, MCP tools, project graph retrieval, artifact persistence, and memory-oriented lineage into one analyst workflow.

## Problem

Software Analysts still perform many repetitive manual tasks:

- gathering requirements and ticket context
- identifying ambiguity and missing information
- checking likely affected implementation areas
- preparing impact findings
- preparing regression test scenarios
- compiling findings into a report

AI tools can help with individual tasks, but the analyst still has to manage the workflow and connect the outputs manually.

## Proposed Solution

The system provides a guided workflow:

```text
Requirement / Ticket
        |
Requirement Analysis
        |
Clarification Needed?
        |
Human Review
        |
Impact Analysis
        |
Test Scenario Generation
        |
Analyst Report
```

## Core Business Rules

1. Every generated claim should be backed by evidence when available.
2. Missing information must be surfaced explicitly instead of hidden.
3. Requirement analysis cannot be reviewed while it still needs clarification.
4. Impact analysis must be created from a reviewed requirement artifact.
5. Test scenarios must be created from a reviewed impact artifact.
6. Every handoff should preserve lineage through `parent_task_id`.

## Implemented Scope

- Requirement analysis skill and endpoint.
- Clarification-answer endpoint.
- `NEEDS_CLARIFICATION` / `READY_FOR_REVIEW` status.
- Software Analyst profile.
- Review-gated requirement-to-impact handoff.
- Review-gated impact-to-test handoff.
- Persisted handoff summary artifact.
- Artifact persistence and evidence history.
- React guided workflow frontend.

## Technical Architecture

See [architecture.md](architecture.md).

High-level layers:

- MCP-backed project graph
- Skill layer
- Software Analyst agent profile
- Coordinator service
- Artifact persistence
- React workflow UI

## Current Limitations

- No autonomous LLM planner yet.
- No production authentication.
- No multi-repo selector yet.
- Issue ingestion is still fixture/local-data oriented.
- Final handoff summary export is not implemented yet.

## Next Practical Steps

1. Add a history view focused on workflow lineage.
2. Add export support for the handoff summary.
3. Add edit/review controls for generated test cases.
4. Connect richer project documents/tickets as input sources.
5. Add an LLM synthesizer behind the existing deterministic skill interfaces.
