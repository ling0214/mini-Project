"""One-shot CLI wrapper around `codebase-memory-mcp`.

This module makes `mcp-server` (an MCP *server* to the Spring Boot backend)
also consume a second, separate tool — `codebase-memory-mcp`, a real
LSP-based, multi-language, type-aware code graph already indexing this repo
(project slug `C-Users-lingn-mini-Project`).

Only `get_endpoint_info`, `trace_impact`, and `get_test_coverage` route
through here — `search_issues` has no code-graph equivalent and stays on the
local `issues.json` path in `graph.py`, unchanged.

Each call shells out to `codebase-memory-mcp.exe cli <tool> --args-file
<path>` rather than holding a persistent MCP-over-stdio session. A
persistent session was the original design, but the `mcp` Python SDK's
`stdio_client()` was verified to hang indefinitely against this executable
on this machine — a raw pipe to the exe answers `initialize` in ~2ms, but
anyio's stdio transport never observed the response (asyncio/anyio-side
issue, not the exe). The one-shot `cli` path is a separate code path the
binary exposes specifically for non-interactive callers, already fast
(<100ms per call measured), so there's no persistent-session benefit being
given up here in practice.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional

CBMM_EXECUTABLE = os.environ.get(
    "CBMM_EXECUTABLE", r"C:\Users\lingn\.local\bin\codebase-memory-mcp.exe"
)
CBMM_PROJECT = os.environ.get("CBMM_PROJECT", "C-Users-lingn-mini-Project")

CODE_CONTEXT_LABELS = {"Route", "Class", "Method", "Function", "File", "Module"}
SKIPPED_CONTEXT_PATH_PARTS = (
    "/.git/",
    "/vendor/",
    "/node_modules/",
    "/storage/framework/",
    "/storage/logs/",
    "/bootstrap/cache/",
)


async def _call_tool(name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
    """Run one codebase-memory-mcp tool via its `cli` subcommand and return its parsed JSON result.

    Raises RuntimeError on a non-zero exit or unparseable stdout rather than
    returning a silently-empty dict — callers here always wrap this in
    try/except so a codebase-memory-mcp outage degrades to "found: false",
    not a crash.
    """
    fd, path = tempfile.mkstemp(suffix=".json")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(arguments, fh)
        proc = await asyncio.create_subprocess_exec(
            CBMM_EXECUTABLE, "cli", name, "--args-file", path,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()
    finally:
        Path(path).unlink(missing_ok=True)

    if proc.returncode != 0:
        raise RuntimeError(
            f"codebase-memory-mcp tool {name!r} exited {proc.returncode}: "
            f"{stderr.decode(errors='replace').strip()}"
        )
    try:
        return json.loads(stdout.decode())
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            f"codebase-memory-mcp tool {name!r} returned non-JSON stdout: "
            f"{stdout.decode(errors='replace')!r}"
        ) from exc


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


async def cbmm_get_architecture(
    project: str, aspects: Optional[List[str]] = None, path: Optional[str] = None
) -> Dict[str, Any]:
    """High-level architecture overview for `project` — packages, layers, and
    inter-package call boundaries. Feeds ArchitectureDiagramService's Mermaid
    generation (fast-onboarding "gen diagram" feature).
    """
    args: Dict[str, Any] = {"project": project}
    if aspects:
        args["aspects"] = aspects
    if path:
        args["path"] = path
    return await _call_tool("get_architecture", args)


async def cbmm_index_repository(repo_path: str, name: Optional[str] = None, mode: str = "fast") -> Dict[str, Any]:
    """Index a repo into codebase-memory-mcp so search_project_context has a
    real graph for it — declaring a workspace (ProjectWorkspaceService) has no
    effect on graph-based retrieval until this has run at least once for that
    project name. `fast` mode skips similarity/semantic edges for a quicker
    first pass; still runs full LSP call/usage resolution.
    """
    args: Dict[str, Any] = {"repo_path": repo_path, "mode": mode}
    if name:
        args["name"] = name
    return await _call_tool("index_repository", args)


async def cbmm_index_status(project: str) -> Dict[str, Any]:
    return await _call_tool("index_status", {"project": project})


async def cbmm_search_project_context(project: str, query: str, limit: int = 12) -> Dict[str, Any]:
    """Retrieve likely affected code context from a selected codebase-memory project.

    This is the first RAG/Memory retrieval path used by the Software Analyst
    workflow: search_graph ranks code graph nodes for the ticket text, then we
    normalize the results into the same trace-like shape ImpactAnalysisSkill
    already understands.
    """
    safe_limit = max(1, min(int(limit or 12), 30))
    result = await _call_tool(
        "search_graph",
        {"project": project, "query": query, "limit": safe_limit * 4},
    )

    matches: List[Dict[str, Any]] = []
    seen = set()
    for item in result.get("results") or []:
        label = item.get("label")
        file_path = item.get("file_path")
        if label not in CODE_CONTEXT_LABELS or not file_path:
            continue
        normalized_path = "/" + str(file_path).replace("\\", "/")
        if any(part in normalized_path for part in SKIPPED_CONTEXT_PATH_PARTS):
            continue

        name = item.get("name") or Path(file_path).stem
        line = item.get("start_line") or 1
        key = (file_path, name)
        if key in seen:
            continue
        seen.add(key)

        qualified_name = item.get("qualified_name") or name
        reason = (
            f"codebase-memory matched {str(label).lower()} {name} "
            f"as relevant to this ticket"
        )
        matches.append(
            {
                "found": True,
                "name": name,
                "file": file_path,
                "line": line,
                "reason": reason,
                "evidence": f"{file_path}:{line}",
                "source": "codebase-memory",
                "label": label,
                "qualified_name": qualified_name,
                "affected": [],
            }
        )
        if len(matches) >= safe_limit:
            break

    return {
        "project": project,
        "query": query,
        "matches": matches,
        "count": len(matches),
        "source": "codebase-memory",
    }
