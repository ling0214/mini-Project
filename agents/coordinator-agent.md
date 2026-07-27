# Agent: Coordinator

## Type

Deterministic workflow coordinator. It does not call an LLM to decide which skill to run.

## Responsibilities

1. Read the active profile from the request. The guided frontend uses `software-analyst`.
2. Check that the profile is allowed to use the requested skill.
3. Run the selected skill and persist the result as an artifact.
4. Enforce human review gates before downstream handoffs.
5. Preserve lineage with `parent_task_id` when one artifact is created from another.

## Handoff Rules

- Clarification can run from a `requirement-analysis` artifact even before review, because clarification is how the analyst resolves missing information.
- Impact analysis can run from a `requirement-analysis` artifact only after that requirement artifact is reviewed.
- Test case generation can run from an `impact-analysis` artifact only after that impact artifact is reviewed.
- Timeline estimation can run from an `impact-analysis` artifact only after that impact artifact is reviewed.

## Artifact Envelope

```json
{
  "schema_version": "artifact.v1",
  "agent": "string",
  "skill": "string",
  "task_id": "string",
  "created_at": "string",
  "result": {},
  "evidence": [],
  "reviewed": false
}
```

`reviewed` flips to `true` only when a human accepts it in the UI.

## Out Of Scope

- No autonomous multi-step planning.
- No LLM-based intent classification.
- No downstream action from an unreviewed artifact.
