# Skill: code-qa

Used by: Software Analyst workflow

## Input

A free-text question about the project (e.g. "what does the checkout endpoint depend on", "which module handles retries").

## Allowed MCP tools

- `get_endpoint_info(name)`
- `trace_impact(file_or_function)`
- `search_issues(query)`

## Procedure

1. Try to resolve named entities in the question against the graph first (not free-text LLM knowledge).
2. Answer strictly from graph facts + retrieved issues; if the question can't be grounded in the graph, say so instead of answering from general LLM knowledge about "what code like this usually does."
3. Every claim in the answer carries an inline reference (`file:line` or `issue#`).

## Output schema

```json
{
  "answer": "string",
  "evidence": [{"claim": "string", "source": "file:line or issue#"}],
  "ungrounded": ["string — parts of the question the graph couldn't answer"]
}
```

## Review gate rule

None required for read-only Q&A — but every answer still must show its evidence inline in the UI, so trust is earned per-answer, not assumed.
