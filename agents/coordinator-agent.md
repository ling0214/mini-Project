# Agent: Coordinator

## Type

Deterministic (no LLM call). The user already tells the system who they are by picking a profile in the UI — adding an LLM to guess "which role is this" would add unpredictability without solving a real problem.

## Responsibilities

1. Read the active profile (`project-analyst` or `tester`) from the session/UI state.
2. Route the incoming request to that profile's agent (`project-analyst-agent` or `tester-agent`).
3. Watch for handoff conditions and surface them as a suggestion, not an automatic action:
   - `impact-analysis` result reviewed + accepted → suggest "send affected modules to Tester agent for test-case-gen"
4. Enforce the review gate at the boundary: an artifact with `reviewed: false` may be passed to another agent as *draft context* only — never presented to a human as a confirmed fact by a downstream agent.

## Handoff artifact envelope

```json
{
  "schema_version": "artifact.v1",
  "agent": "string — which agent produced this",
  "skill": "string — which skill produced this",
  "task_id": "string",
  "created_at": "string",
  "result": {},
  "evidence": [],
  "reviewed": false
}
```

`reviewed` flips to `true` only when a human accepts it in the UI. This mirrors the artifact-handoff discipline already proven in a production incident-response multi-agent pipeline: no confirmed finding without evidence, no downstream action on an unreviewed claim.

## Explicitly out of scope for v1

- No autonomous multi-step planning across agents (e.g. auto-running test-case-gen without a human accepting the impact analysis first).
- No LLM-based intent classification. If this becomes necessary later (e.g. free-text input where the role isn't obvious), it's a scoped addition here, not a rewrite.
