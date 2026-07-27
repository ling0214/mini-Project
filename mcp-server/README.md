# mcp-server

Python MCP server exposing 4 project-graph tools. Three of them
(`get_endpoint_info`, `trace_impact`, `get_test_coverage`) are backed by
[`codebase-memory-mcp`](https://github.com/DeusData/codebase-memory-mcp), a
real LSP-based, multi-language, type-aware code graph — see
`mcp_server/cbmm_client.py`, which makes this server act as an MCP *client* of
that second MCP server internally. The fourth, `search_issues`, has no
code-graph equivalent and still reads a local `issues.json` fixture via the
original `ast`-based graph in `graph.py`. See
[docs/architecture.md](../docs/architecture.md) for the full design and why
this replaced the original single-repo `ast`-only engine.

## Status

All 4 tools implemented. `get_endpoint_info` / `trace_impact` /
`get_test_coverage` query `codebase-memory-mcp` (project slug set by
`CBMM_PROJECT`, default `C-Users-lingn-mini-Project` — this repo indexing
itself). `search_issues` is unchanged, still demoed against the small
self-contained `sample_target/` fixture (a fake checkout flow + hand-authored
`issues.json`).

## Setup

```bash
python -m venv .venv
./.venv/Scripts/python.exe -m pip install -e ".[dev]"
```

`codebase-memory-mcp` must already be installed and have indexed the target
project once (`codebase-memory-mcp cli index_repository --repo-path <path> --mode full`)
before `get_endpoint_info`/`trace_impact`/`get_test_coverage` will resolve
anything — this server does not index on your behalf, it only queries an
already-built graph.

## Run tests

```bash
./.venv/Scripts/python.exe -m pytest -v
```

`tests/test_cbmm_client.py` mocks the codebase-memory-mcp call boundary
(`cbmm_client._call_tool`) so it runs fast with no real subprocess.
`tests/test_graph.py` still covers the untouched `search_issues` path (and
the now-unused-by-the-other-3-tools ast graph, kept for that reason — see
"Known limitations" below).

## Run the server (stdio transport)

```bash
./.venv/Scripts/python.exe -m mcp_server.server
```

Two independent env-var groups, since the 4 tools now have two independent
data sources:

```bash
# Which codebase-memory-mcp project the 3 graph tools query (default: this repo)
CBMM_PROJECT=C-Users-lingn-mini-Project
CBMM_EXECUTABLE=C:\Users\lingn\.local\bin\codebase-memory-mcp.exe   # default shown

# Where search_issues reads its local issues.json from (unchanged from before)
MCP_TARGET_DIR=/path/to/some/repo MCP_ISSUES_PATH=/path/to/issues.json
```

## Tools

| Tool | Input | Backed by | Notes |
|---|---|---|---|
| `get_endpoint_info` | `name: str` | codebase-memory-mcp | `{"found": false}` if the name isn't in the graph. If multiple symbols share the bare name (real, expected once a project has files/classes reusing a short helper name), returns the first match plus a `note` field naming the count — never silently guesses without saying so. |
| `trace_impact` | `name: str, max_hops: int = 2` | codebase-memory-mcp | Blast radius via `trace_path`; `max_hops` is enforced client-side regardless of what the underlying tool returns. |
| `get_test_coverage` | `name: str` | codebase-memory-mcp | Uses the real `TESTS` edge type — a purpose-built graph edge, not a "function name starts with `test`" naming guess (the old engine's approach). |
| `search_issues` | `query: str` | local `issues.json` (unchanged) | Whole-word keyword match against issue title + body, with a stopword list so common words in a question ("what", "does", "change"...) can't produce a false match. |

## Known limitations

- `search_issues` reads a local `issues.json` fixture, not the live GitHub Issues API yet — keeps the demo runnable offline with no token/auth setup required. Out of scope for the codebase-memory-mcp swap (no code-graph equivalent exists).
- The original `ast`-based `graph.py`/`ProjectGraph` call-graph construction is still in the codebase (used only for `search_issues`'s issue-loading now) — removing the now-dead call-graph code it also builds is a follow-up cleanup, not done in this pass, to keep this change's diff focused and easy to review/revert.
- Single fixed target project per process (`CBMM_PROJECT`) — no multi-repo/project-selector flow yet; a `projects` table/UI only becomes real once that's built (tracked separately in `docs/architecture.md`).
