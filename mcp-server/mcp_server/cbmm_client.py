"""MCP stdio client wrapper around `codebase-memory-mcp`.

This module makes `mcp-server` (an MCP *server* to the Spring Boot backend)
also act as an MCP *client* of a second, separate MCP server —
`codebase-memory-mcp`, a real LSP-based, multi-language, type-aware code
graph already indexing this repo (project slug `C-Users-lingn-mini-Project`).

Only `get_endpoint_info`, `trace_impact`, and `get_test_coverage` route
through here — `search_issues` has no code-graph equivalent and stays on the
local `issues.json` path in `graph.py`, unchanged.

One `ClientSession` is spawned lazily and reused for the life of the process
(spawning `codebase-memory-mcp.exe` per call would be needlessly slow and
would lose any warm state the server keeps).
"""

from __future__ import annotations

import asyncio
import json
import os
import re
from typing import Any, Dict, List, Optional

from mcp import ClientSession
from mcp.client.stdio import StdioServerParameters, stdio_client

CBMM_EXECUTABLE = os.environ.get(
    "CBMM_EXECUTABLE", r"C:\Users\lingn\.local\bin\codebase-memory-mcp.exe"
)
CBMM_PROJECT = os.environ.get("CBMM_PROJECT", "C-Users-lingn-mini-Project")

_session: Optional[ClientSession] = None
_session_lock = asyncio.Lock()
_stdio_ctx = None
_session_ctx = None


async def _get_session() -> ClientSession:
    global _session, _stdio_ctx, _session_ctx
    if _session is not None:
        return _session
    async with _session_lock:
        if _session is not None:  # re-check after acquiring the lock
            return _session
        # --ui=false: a headless graph-query client has no use for
        # codebase-memory-mcp's HTTP visualization UI, and leaving it enabled
        # in this environment was observed to hang the stdio handshake
        # (verified: bare invocation left stdout empty and the client never
        # got a response; --ui=false produced clean stderr-only logging and
        # a clean immediate shutdown on EOF).
        params = StdioServerParameters(command=CBMM_EXECUTABLE, args=["--ui=false"])
        _stdio_ctx = stdio_client(params)
        read, write = await _stdio_ctx.__aenter__()
        _session_ctx = ClientSession(read, write)
        _session = await _session_ctx.__aenter__()
        await _session.initialize()
        return _session


async def _call_tool(name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    """Call a codebase-memory-mcp tool and return its parsed JSON result.

    Raises RuntimeError on a tool-reported error rather than returning a
    silently-empty dict — callers here always wrap this in try/except so a
    codebase-memory-mcp outage degrades to "found: false", not a crash.
    """
    session = await _get_session()
    result = await session.call_tool(name, arguments)
    if getattr(result, "isError", False):
        raise RuntimeError(f"codebase-memory-mcp tool {name!r} reported an error: {result.content}")
    structured = getattr(result, "structuredContent", None)
    if isinstance(structured, dict):
        return structured
    for block in result.content or []:
        text = getattr(block, "text", None)
        if text:
            return json.loads(text)
    raise RuntimeError(f"codebase-memory-mcp tool {name!r} returned no parseable content")


def _escape(value: str) -> str:
    """Defensive single-quote escape for values interpolated into a Cypher string."""
    return value.replace("\\", "\\\\").replace("'", "\\'")


async def resolve_symbol(project: str, name: str) -> Optional[Dict[str, Any]]:
    """Resolve a bare symbol name to one graph node via search_graph.

    Anchored exact match on the node's own `name` property (not
    qualified_name) — matches the old graph.py's flat "function name ->
    node" lookup semantics as closely as possible. Returns None if nothing
    matches. If more than one node shares this bare name (a real, expected
    case once multiple files/classes share a short helper name), returns the
    first match plus an `_ambiguous_count` key so callers can be honest about
    it in an added `note` field, instead of silently guessing or discarding
    the extra matches.
    """
    pattern = f"^{re.escape(name)}$"
    result = await _call_tool(
        "search_graph", {"project": project, "name_pattern": pattern}
    )
    results = result.get("results") or []
    if not results:
        return None
    best = dict(results[0])

    # search_graph's result payload doesn't include start_line/end_line (verified
    # live) even though the underlying node has them — fetch it explicitly so
    # every caller gets an accurate "line" without repeating this lookup.
    qn = best["qualified_name"]
    loc = await _call_tool(
        "query_graph",
        {
            "project": project,
            "query": (
                f"MATCH (n {{qualified_name: '{_escape(qn)}'}}) "
                "RETURN n.file_path AS file, n.start_line AS line"
            ),
        },
    )
    rows = loc.get("rows") or []
    if rows:
        best["file_path"] = rows[0][0]
        best["start_line"] = rows[0][1]

    if len(results) > 1:
        best["_ambiguous_count"] = len(results)
    return best


async def cbmm_get_endpoint_info(name: str) -> Optional[Dict[str, Any]]:
    node = await resolve_symbol(CBMM_PROJECT, name)
    if node is None:
        return None
    qn = node["qualified_name"]

    calls_result = await _call_tool(
        "query_graph",
        {
            "project": CBMM_PROJECT,
            "query": (
                f"MATCH (n {{qualified_name: '{_escape(qn)}'}})-[:CALLS]->(callee) "
                "RETURN DISTINCT callee.name AS name"
            ),
        },
    )
    called_by_result = await _call_tool(
        "query_graph",
        {
            "project": CBMM_PROJECT,
            "query": (
                f"MATCH (caller)-[:CALLS]->(n {{qualified_name: '{_escape(qn)}'}}) "
                "RETURN DISTINCT caller.name AS name"
            ),
        },
    )
    calls = [row[0] for row in calls_result.get("rows") or []]
    called_by = [row[0] for row in called_by_result.get("rows") or []]

    out: Dict[str, Any] = {
        "name": node["name"],
        "file": node.get("file_path"),
        "line": node.get("start_line"),
        "calls": calls,
        "called_by": called_by,
    }
    if node.get("_ambiguous_count"):
        out["note"] = (
            f"{node['_ambiguous_count']} symbols share the name {name!r}; "
            f"showing {qn}"
        )
    return out


async def cbmm_trace_impact(name: str, max_hops: int = 2) -> Optional[Dict[str, Any]]:
    node = await resolve_symbol(CBMM_PROJECT, name)
    if node is None:
        return None
    qn = node["qualified_name"]

    trace = await _call_tool(
        "trace_path",
        {"project": CBMM_PROJECT, "function_name": qn, "mode": "calls"},
    )
    if trace.get("status") == "ambiguous":
        # We already resolved to one concrete qualified_name above, so this
        # shouldn't normally fire — but if trace_path's own resolver disagrees
        # with search_graph's, fail honestly rather than guess a second time.
        return None

    callees = [
        {**entry, "relation": "calls"}
        for entry in (trace.get("callees") or [])
        if entry.get("hop", 0) <= max_hops
    ]
    callers = [
        {**entry, "relation": "called_by"}
        for entry in (trace.get("callers") or [])
        if entry.get("hop", 0) <= max_hops
    ]
    combined = callees + callers
    if not combined:
        affected: List[Dict[str, Any]] = []
    else:
        qn_list = ", ".join(f"'{_escape(e['qualified_name'])}'" for e in combined)
        loc_result = await _call_tool(
            "query_graph",
            {
                "project": CBMM_PROJECT,
                "query": (
                    f"MATCH (n) WHERE n.qualified_name IN [{qn_list}] "
                    "RETURN n.qualified_name AS qn, n.file_path AS file, n.start_line AS line"
                ),
            },
        )
        locations = {
            row[0]: {"file": row[1], "line": row[2]}
            for row in loc_result.get("rows") or []
        }
        affected = [
            {
                "name": entry["name"],
                "file": locations.get(entry["qualified_name"], {}).get("file"),
                "line": locations.get(entry["qualified_name"], {}).get("line"),
                "hops": entry.get("hop"),
                "relation": entry["relation"],
            }
            for entry in combined
        ]

    out: Dict[str, Any] = {
        "name": node["name"],
        "file": node.get("file_path"),
        "line": node.get("start_line"),
        "affected": affected,
    }
    if node.get("_ambiguous_count"):
        out["note"] = (
            f"{node['_ambiguous_count']} symbols share the name {name!r}; "
            f"showing {qn}"
        )
    return out


async def cbmm_get_test_coverage(name: str) -> Optional[Dict[str, Any]]:
    node = await resolve_symbol(CBMM_PROJECT, name)
    if node is None:
        return None
    qn = node["qualified_name"]

    result = await _call_tool(
        "query_graph",
        {
            "project": CBMM_PROJECT,
            "query": (
                f"MATCH (t)-[:TESTS]->(target {{qualified_name: '{_escape(qn)}'}}) "
                "RETURN t.name AS test, t.file_path AS file, t.start_line AS line"
            ),
        },
    )
    covered_by = [
        {"test": row[0], "file": row[1], "line": row[2]}
        for row in result.get("rows") or []
    ]

    out: Dict[str, Any] = {"name": node["name"], "covered_by": covered_by}
    if node.get("_ambiguous_count"):
        out["note"] = (
            f"{node['_ambiguous_count']} symbols share the name {name!r}; "
            f"showing {qn}"
        )
    return out
