"""MCP server exposing project-graph tools over stdio.

`get_endpoint_info`, `trace_impact`, and `get_test_coverage` are backed by
`codebase-memory-mcp` (see cbmm_client.py) — a real LSP-based, multi-language,
type-aware code graph, already indexing this repo (project slug set by the
CBMM_PROJECT env var, default `C-Users-lingn-mini-Project`). This replaces the
original Week 1-3 ast-based graph for these 3 tools, which assumed unique
function names and resolved calls syntactically (see graph.py's docstrings).

`search_issues` is unchanged — it has no code-graph equivalent, so it still
reads the local ast-graph's loaded issues.json via ProjectGraph.

Run: mcp-server/.venv/Scripts/python.exe -m mcp_server.server
"""

import os
from pathlib import Path

from mcp.server.fastmcp import FastMCP

from mcp_server import cbmm_client
from mcp_server.graph import ProjectGraph

DEFAULT_TARGET = Path(__file__).resolve().parent.parent / "sample_target"

mcp = FastMCP("mini-project-graph")

_target_dir = Path(os.environ.get("MCP_TARGET_DIR", DEFAULT_TARGET))
_issues_path = Path(os.environ.get("MCP_ISSUES_PATH", _target_dir / "issues.json"))
# Only used for search_issues now — the ast call-graph this also builds is no
# longer read by the other 3 tools, but building it is cheap and this keeps
# the diff minimal (see docs/architecture.md for the follow-up cleanup note).
_graph = ProjectGraph.build(_target_dir, _issues_path)


@mcp.tool()
async def get_endpoint_info(name: str) -> dict:
    """Look up a function/endpoint by name: file, line, what it calls, what calls it.

    Returns {"found": false} if the name isn't in the codebase-memory-mcp
    graph — callers must treat that as "insufficient graph coverage", not
    silently move on.
    """
    info = await cbmm_client.cbmm_get_endpoint_info(name)
    if info is None:
        return {"found": False, "name": name}
    return {"found": True, **info}


@mcp.tool()
def search_issues(query: str) -> dict:
    """Search project issues by keyword match against title + body."""
    matches = _graph.search_issues(query)
    return {"query": query, "matches": matches, "count": len(matches)}


@mcp.tool()
async def trace_impact(name: str, max_hops: int = 2) -> dict:
    """Blast radius from a function/entry point: what it transitively calls
    and what transitively calls it, out to max_hops in each direction.

    Returns {"found": false} if the name isn't in the codebase-memory-mcp
    graph — callers must treat that as "insufficient graph coverage", not
    silently move on.
    """
    result = await cbmm_client.cbmm_trace_impact(name, max_hops=max_hops)
    if result is None:
        return {"found": False, "name": name}
    return {"found": True, **result}


@mcp.tool()
async def get_test_coverage(name: str) -> dict:
    """Which tests cover `name`, per codebase-memory-mcp's TESTS edge — a
    real, purpose-built edge type, not a "name starts with test" guess.

    Returns {"found": false} if the name isn't in the graph. An empty
    covered_by list is a real, meaningful result (no coverage found), not
    an error — callers should treat it as a coverage gap to fill.
    """
    result = await cbmm_client.cbmm_get_test_coverage(name)
    if result is None:
        return {"found": False, "name": name}
    return {"found": True, **result}


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
