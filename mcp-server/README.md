# mcp-server

Python MCP server. Parses a target directory's `.py` files into a project graph (functions + call edges, via `ast` — no code execution) and exposes it as MCP tools. See [docs/architecture.md](../docs/architecture.md) for the full design.

## Status

Week 1 slice implemented: `get_endpoint_info`, `search_issues`, tested against a small self-contained `sample_target/` fixture (a fake checkout flow) so the server runs with zero setup before a real public demo repo is wired in. `trace_impact` and `get_test_coverage` land in Week 2–3.

## Setup

```bash
python -m venv .venv
./.venv/Scripts/python.exe -m pip install -e ".[dev]"
```

## Run tests

```bash
./.venv/Scripts/python.exe -m pytest -v
```

## Run the server (stdio transport)

```bash
./.venv/Scripts/python.exe -m mcp_server.server
```

By default it parses `sample_target/`. Point it at a real repo instead:

```bash
MCP_TARGET_DIR=/path/to/some/repo MCP_ISSUES_PATH=/path/to/issues.json \
  ./.venv/Scripts/python.exe -m mcp_server.server
```

## Tools

| Tool | Input | Notes |
|---|---|---|
| `get_endpoint_info` | `name: str` | Returns `{"found": false}` if the name isn't in the graph — callers must treat that as missing coverage, not silently move on. |
| `search_issues` | `query: str` | Whole-word keyword match against issue title + body, with a stopword list so common words in a question ("what", "does", "change"...) can't produce a false match. |

## Known limitations (MVP, called out on purpose)

- Function names are assumed unique across the whole target — fine for `sample_target/` and small demo repos, wrong for large multi-module projects with name collisions. Flagged here rather than silently producing wrong answers later.
- Call resolution is syntactic (`ast.Call` → name/attr), not type-resolved — a method call and an unrelated function that happens to share a name will look the same. Good enough for Week 1; revisit if the chosen demo repo makes this a real problem.
- `search_issues` reads a local `issues.json` fixture, not the live GitHub Issues API yet — keeps the demo runnable offline with no token/auth setup required.
