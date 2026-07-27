# Skill: handoff-summary

Used by: Software Analyst workflow

## Input

A reviewed `requirement-analysis` artifact, a reviewed `impact-analysis` artifact, and any generated `test-case-gen` artifacts.

## Procedure

1. Load the reviewed requirement artifact and its original input text.
2. Load the reviewed impact artifact.
3. Load linked or selected test-case artifacts.
4. Compile a shareable working summary for PM, developer, tester, or supervisor.
5. Preserve evidence from the source artifacts.

## Output Schema

```json
{
  "requirement_summary": "string",
  "business_rules": ["string"],
  "clarifications": ["string"],
  "assumptions": ["string"],
  "impact_areas": [{"name": "string", "path": "string", "reason": "string"}],
  "risk_notes": [{"note": "string", "evidence": "string"}],
  "risk_level": "low | medium | elevated | unknown",
  "effort_estimate": "string",
  "test_plans": [{"target": "string", "case_count": 0, "regression_checklist": ["string"]}],
  "open_questions": ["string"],
  "evidence": [{"claim": "string", "source": "string"}]
}
```

## Review Gate

The handoff summary is persisted as its own artifact with `reviewed: false`. The analyst reviews it before treating it as the shareable workflow output.
