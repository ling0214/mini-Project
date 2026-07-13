# Skill: impact-analysis

Used by: Project Analyst

## Input

A free-text change request (e.g. "add biometric login as an alternative to PIN on the payment confirmation screen").

## Allowed MCP tools

- `trace_impact(file_or_function)`
- `search_issues(query)`

## Procedure

1. Extract candidate entry points from the request (feature area, screen, endpoint names) — ask the graph, don't guess from the text alone.
2. Call `trace_impact` on each candidate entry point found in the graph.
3. Call `search_issues` for related past issues (regressions, prior similar requests) to ground risk notes in real precedent instead of generic AI guesses.
4. If no entry point resolves in the graph, stop and report "insufficient graph coverage" — do not fabricate an impact list.

## Output schema

```json
{
  "affected_modules": [{"path": "string", "reason": "string", "evidence": "graph-node-id or issue#"}],
  "risk_notes": [{"note": "string", "evidence": "issue# or graph-node-id"}],
  "rough_effort": {"estimate": "S | M | L", "basis": "string"},
  "missing_evidence": ["string — graph gaps or unresolved entry points"],
  "confidence": "low | medium | high"
}
```

## Review gate rule

Never returned as "confirmed" to the user directly — the harness renders this as a draft with every claim's evidence link visible, and a human must accept it before it's saved as the canonical impact analysis for that change request.
