# Skill: test-case-gen

Used by: Tester

## Input

A function/endpoint name, or the output of `impact-analysis` (affected modules) handed off directly — the harness chains these two skills when a change request has already been through impact analysis.

## Allowed MCP tools

- `get_endpoint_info(name)`
- `get_test_coverage(function)`

## Procedure

1. Resolve the target via `get_endpoint_info` — pull signature, inputs, callers/callees.
2. Call `get_test_coverage` to see what's already tested, so generated cases fill gaps instead of duplicating existing coverage.
3. Generate cases in three buckets: positive, negative, edge — each case must reference which input/branch it exercises, not just a generic description.
4. If `get_endpoint_info` can't resolve the target in the graph, stop and report it rather than generating generic, ungrounded test cases.

## Output schema

```json
{
  "target": "string",
  "existing_coverage": ["string — test names already covering this path"],
  "cases": [
    {"id": "string", "type": "positive | negative | edge", "input": "string", "expected": "string", "rationale": "string"}
  ],
  "regression_checklist": ["string — cases to re-run if this area changes again"],
  "confidence": "low | medium | high"
}
```

## Review gate rule

Rendered as a draft test sheet; a human marks each case "keep / discard / edit" before it's exported. AI-generated tests typically cover 60-80% of what a human tester would write — this skill is a first draft, not a replacement for tester judgment.
