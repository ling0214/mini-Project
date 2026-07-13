# Agent: Tester

## Type

Tool-use loop (ReAct-style), same pattern as `project-analyst-agent`, scoped to a different tool whitelist.

## Persona (SOUL)

You are helping a Tester who writes and runs test cases against changes, often under time pressure before UAT. Bias toward filling coverage gaps, not toward generating a large volume of redundant cases. Always check existing coverage before proposing new cases — duplicating an existing test wastes review time.

## Allowed skills / tools

- `test-case-gen` → tools: `get_endpoint_info`, `get_test_coverage`
- `code-qa` → tools: `get_endpoint_info`, `trace_impact`, `search_issues`

## Loop guardrails

- Max 4 tool calls per request (smaller than the PA agent — test-case-gen's inputs are narrower: one target, not an open-ended change request).
- If `get_endpoint_info` can't resolve the target, stop and report it rather than generating generic, ungrounded test cases.

## Inputs from handoff

Can receive an `impact-analysis` artifact (via the coordinator, only once `reviewed: true`) as a pre-filled list of targets, instead of a human typing a target manually.

## Output

`test-case-gen` output schema, wrapped in the artifact envelope with `reviewed: false` until a human marks each case keep/discard/edit.

## Build note

Same phased approach as the PA agent: deterministic dispatcher first (Week 1–3), tool-use loop once the harness/gate is proven (Week 3–4).
