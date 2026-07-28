"""Tests for cbmm_client.py — the codebase-memory-mcp-backed replacement for
get_endpoint_info/trace_impact/get_test_coverage's old ast-graph implementation.

`_call_tool` (the one low-level function every codebase-memory-mcp call routes
through) is mocked so these run fast with no real subprocess — the real
end-to-end round trip is verified separately, live, against the real
`codebase-memory-mcp.exe` (see docs/architecture.md's verification note).
"""

import asyncio
from unittest.mock import AsyncMock, patch

from mcp_server import cbmm_client


def run(coro):
    return asyncio.run(coro)


def _search_graph_result(*matches):
    return {"total": len(matches), "results": list(matches), "has_more": False}


def _rows(*rows):
    return {"rows": [list(r) for r in rows]}


ENDPOINT_INFO_NODE = {
    "name": "get_endpoint_info",
    "qualified_name": "pkg.graph.ProjectGraph.get_endpoint_info",
    "label": "Method",
    "file_path": "mcp_server/graph.py",
}
ENDPOINT_INFO_NODE_2 = {
    "name": "get_endpoint_info",
    "qualified_name": "pkg.server.get_endpoint_info",
    "label": "Function",
    "file_path": "mcp_server/server.py",
}


class TestResolveSymbol:
    def test_single_match_enriches_with_line(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "query_graph":
                return _rows(["mcp_server/graph.py", 84])
            raise AssertionError(f"unexpected tool {name}")

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            node = run(cbmm_client.resolve_symbol("proj", "get_endpoint_info"))

        assert node["qualified_name"] == "pkg.graph.ProjectGraph.get_endpoint_info"
        assert node["file_path"] == "mcp_server/graph.py"
        assert node["start_line"] == 84
        assert "_ambiguous_count" not in node

    def test_no_match_returns_none(self):
        with patch.object(
            cbmm_client, "_call_tool", AsyncMock(return_value=_search_graph_result())
        ):
            assert run(cbmm_client.resolve_symbol("proj", "does_not_exist")) is None

    def test_multiple_matches_marks_ambiguous_but_still_returns_first(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE, ENDPOINT_INFO_NODE_2)
            if name == "query_graph":
                return _rows(["mcp_server/graph.py", 84])
            raise AssertionError(f"unexpected tool {name}")

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            node = run(cbmm_client.resolve_symbol("proj", "get_endpoint_info"))

        assert node["qualified_name"] == "pkg.graph.ProjectGraph.get_endpoint_info"
        assert node["_ambiguous_count"] == 2


class TestGetEndpointInfo:
    def test_found_returns_calls_and_called_by(self):
        calls = []

        async def fake_call_tool(name, args):
            calls.append((name, args))
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "query_graph":
                query = args["query"]
                if "start_line" in query:
                    return _rows(["mcp_server/graph.py", 84])
                if "-[:CALLS]->(callee)" in query:
                    return _rows(["get"], ["callers_of"])
                if "(caller)-[:CALLS]->" in query:
                    return _rows(["get_endpoint_info_tool"])
            raise AssertionError(f"unexpected call: {name} {args}")

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_get_endpoint_info("get_endpoint_info"))

        assert result["file"] == "mcp_server/graph.py"
        assert result["line"] == 84
        assert set(result["calls"]) == {"get", "callers_of"}
        assert result["called_by"] == ["get_endpoint_info_tool"]
        assert "note" not in result

    def test_ambiguous_match_adds_note_but_still_returns_a_result(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE, ENDPOINT_INFO_NODE_2)
            if name == "query_graph":
                return _rows(["mcp_server/graph.py", 84]) if "start_line" in args["query"] else _rows()
            raise AssertionError

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_get_endpoint_info("get_endpoint_info"))

        assert "2 symbols share the name" in result["note"]

    def test_not_found_returns_none(self):
        with patch.object(
            cbmm_client, "_call_tool", AsyncMock(return_value=_search_graph_result())
        ):
            assert run(cbmm_client.cbmm_get_endpoint_info("does_not_exist")) is None


class TestTraceImpact:
    def test_found_builds_affected_list_with_locations(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "trace_path":
                return {
                    "function": ENDPOINT_INFO_NODE["qualified_name"],
                    "direction": "both",
                    "mode": "calls",
                    "callees": [{"name": "callers_of", "qualified_name": "pkg.graph.ProjectGraph.callers_of", "hop": 1}],
                    "callers": [],
                }
            if name == "query_graph":
                query = args["query"]
                if "start_line" in query and "IN [" not in query:
                    return _rows(["mcp_server/graph.py", 84])
                if "IN [" in query:
                    return _rows(["pkg.graph.ProjectGraph.callers_of", "mcp_server/graph.py", 81])
            raise AssertionError(f"unexpected call: {name} {args}")

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_trace_impact("get_endpoint_info", max_hops=2))

        assert result["file"] == "mcp_server/graph.py"
        assert result["line"] == 84
        assert result["affected"] == [
            {"name": "callers_of", "file": "mcp_server/graph.py", "line": 81, "hops": 1, "relation": "calls"}
        ]

    def test_respects_max_hops_client_side(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "trace_path":
                return {
                    "callees": [
                        {"name": "a", "qualified_name": "pkg.a", "hop": 1},
                        {"name": "b", "qualified_name": "pkg.b", "hop": 3},
                    ],
                    "callers": [],
                }
            if name == "query_graph":
                query = args["query"]
                if "IN [" in query:
                    return _rows(["pkg.a", "file_a.py", 1])
                return _rows(["mcp_server/graph.py", 84])
            raise AssertionError

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_trace_impact("get_endpoint_info", max_hops=2))

        names = {item["name"] for item in result["affected"]}
        assert names == {"a"}  # hop=3 entry filtered out by max_hops=2

    def test_trace_path_ambiguous_status_is_treated_as_not_found(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "trace_path":
                return {"status": "ambiguous", "suggestions": []}
            if name == "query_graph":
                return _rows(["mcp_server/graph.py", 84])
            raise AssertionError

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            assert run(cbmm_client.cbmm_trace_impact("get_endpoint_info")) is None

    def test_not_found_returns_none(self):
        with patch.object(
            cbmm_client, "_call_tool", AsyncMock(return_value=_search_graph_result())
        ):
            assert run(cbmm_client.cbmm_trace_impact("does_not_exist")) is None


class TestGetTestCoverage:
    def test_found_returns_covered_by(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "query_graph":
                query = args["query"]
                if "TESTS" in query:
                    return _rows(["test_endpoint_info_resolves_calls_and_callers", "tests/test_graph.py", 12])
                return _rows(["mcp_server/graph.py", 84])
            raise AssertionError

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_get_test_coverage("get_endpoint_info"))

        assert result["covered_by"] == [
            {"test": "test_endpoint_info_resolves_calls_and_callers", "file": "tests/test_graph.py", "line": 12}
        ]

    def test_empty_coverage_is_a_real_result_not_none(self):
        async def fake_call_tool(name, args):
            if name == "search_graph":
                return _search_graph_result(ENDPOINT_INFO_NODE)
            if name == "query_graph":
                query = args["query"]
                return _rows() if "TESTS" in query else _rows(["mcp_server/graph.py", 84])
            raise AssertionError

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_get_test_coverage("get_endpoint_info"))

        assert result is not None
        assert result["covered_by"] == []

    def test_not_found_returns_none(self):
        with patch.object(
            cbmm_client, "_call_tool", AsyncMock(return_value=_search_graph_result())
        ):
            assert run(cbmm_client.cbmm_get_test_coverage("does_not_exist")) is None


class TestSearchProjectContext:
    def test_search_graph_results_are_normalized_for_impact_analysis(self):
        async def fake_call_tool(name, args):
            assert name == "search_graph"
            assert args["project"] == "MyBanjirCare"
            assert args["query"] == "donor aid request city urgency"
            assert args["limit"] == 8
            return _search_graph_result(
                {
                    "name": "AidRequestController",
                    "qualified_name": "MyBanjirCare.app.Http.Controllers.AidRequestController",
                    "label": "Class",
                    "file_path": "app/Http/Controllers/AidRequestController.php",
                    "start_line": 12,
                },
                {
                    "name": "IgnoredVendor",
                    "qualified_name": "vendor.IgnoredVendor",
                    "label": "Class",
                    "file_path": "vendor/package/IgnoredVendor.php",
                    "start_line": 1,
                },
                {
                    "name": "noise",
                    "qualified_name": "MyBanjirCare.noise",
                    "label": "Variable",
                    "file_path": "app/noise.php",
                    "start_line": 1,
                },
            )

        with patch.object(cbmm_client, "_call_tool", AsyncMock(side_effect=fake_call_tool)):
            result = run(cbmm_client.cbmm_search_project_context(
                "MyBanjirCare", "donor aid request city urgency", limit=2))

        assert result["source"] == "codebase-memory"
        assert result["count"] == 1
        assert result["matches"] == [
            {
                "found": True,
                "name": "AidRequestController",
                "file": "app/Http/Controllers/AidRequestController.php",
                "line": 12,
                "reason": "codebase-memory matched class AidRequestController as relevant to this ticket",
                "evidence": "app/Http/Controllers/AidRequestController.php:12",
                "source": "codebase-memory",
                "label": "Class",
                "qualified_name": "MyBanjirCare.app.Http.Controllers.AidRequestController",
                "affected": [],
            }
        ]
