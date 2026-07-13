"""MCP server exposing project-graph tools over stdio.

Week 1 scope (see docs/architecture.md): get_endpoint_info + search_issues,
built from the ast-based graph in graph.py. trace_impact and
get_test_coverage land in Week 2-3 once impact-analysis / test-case-gen
are being wired up.

Run: mcp-server/.venv/Scripts/python.exe -m mcp_server.server
"""

import os
from pathlib import Path

from mcp.server.fastmcp import FastMCP

from mcp_server.graph import ProjectGraph

DEFAULT_TARGET = Path(__file__).resolve().parent.parent / "sample_target"

mcp = FastMCP("mini-project-graph")

_target_dir = Path(os.environ.get("MCP_TARGET_DIR", DEFAULT_TARGET))
_issues_path = Path(os.environ.get("MCP_ISSUES_PATH", _target_dir / "issues.json"))
_graph = ProjectGraph.build(_target_dir, _issues_path)


@mcp.tool()
def get_endpoint_info(name: str) -> dict:
    """Look up a function/endpoint by name: file, line, what it calls, what calls it.

    Returns {"found": false} if the name isn't in the graph — callers must
    treat that as "insufficient graph coverage", not silently move on.
    """
    info = _graph.get_endpoint_info(name)
    if info is None:
        return {"found": False, "name": name}
    return {"found": True, **info}


@mcp.tool()
def search_issues(query: str) -> dict:
    """Search project issues by keyword match against title + body."""
    matches = _graph.search_issues(query)
    return {"query": query, "matches": matches, "count": len(matches)}


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
