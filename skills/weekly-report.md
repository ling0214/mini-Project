# Skill: weekly-report

Used by: Project Analyst

## Input

A date range (defaults to the last 7 days).

## Allowed MCP tools

- `search_issues(query)`

## Procedure

1. Pull issue state deltas in range: opened, closed, blocked, moved between states.
2. Group into: completed, in-progress, blocked, upcoming.
3. Flag anything stale (open > N days with no state change) as a timeline risk — this is the one place the skill is allowed to add an opinion beyond raw facts, and it must say why (e.g. "open 12 days, no update").

## Output schema

```json
{
  "period": {"from": "date", "to": "date"},
  "completed": ["issue# — title"],
  "in_progress": ["issue# — title"],
  "blocked": ["issue# — title, reason if known"],
  "timeline_risks": [{"issue": "issue#", "reason": "string"}]
}
```

## Review gate rule

Draft only — a PA edits tone/framing before it goes out as an actual status update. This skill produces the data pull, not the final customer-facing wording.
