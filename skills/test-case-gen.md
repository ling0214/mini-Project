# Skill: test-case-gen

Used by: Software Analyst workflow

## Input

A function/endpoint name, or the output of `impact-analysis` handed off from a reviewed impact artifact. In the guided workflow this happens after requirement analysis, clarification, review, and impact analysis.

## Allowed MCP tools

- `get_endpoint_info(name)`
- `get_test_coverage(function)`

## Procedure

1. Resolve the target via `get_endpoint_info`.
2. Call `get_test_coverage` to see what is already tested.
3. Generate cases in three buckets: positive, negative, and edge.
4. If the graph cannot resolve the target, report the gap instead of generating generic cases.

## Output Schema

```json
{
  "target": "string",
  "existing_coverage": ["string"],
  "cases": [
    {"id": "string", "type": "positive | negative | edge", "input": "string", "expected": "string", "rationale": "string"}
  ],
  "regression_checklist": ["string"],
  "confidence": "low | medium | high"
}
```

## Review Gate

The result is a draft test sheet. A human analyst remains responsible for accepting, editing, or discarding generated cases.
