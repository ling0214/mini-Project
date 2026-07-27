from pathlib import Path

from mcp_server.graph import ProjectGraph

SAMPLE_TARGET = Path(__file__).resolve().parent.parent / "sample_target"


def build_graph() -> ProjectGraph:
    return ProjectGraph.build(SAMPLE_TARGET, SAMPLE_TARGET / "issues.json")


def test_endpoint_info_resolves_calls_and_callers():
    graph = build_graph()

    info = graph.get_endpoint_info("checkout_endpoint")
    assert info is not None
    assert info["file"] == "app.py"
    assert set(info["calls"]) >= {"calculate_total", "charge_card", "send_receipt"}
    assert info["called_by"] == []  # nothing in the sample calls the top-level endpoint


def test_endpoint_info_finds_callers_of_a_leaf_function():
    graph = build_graph()

    info = graph.get_endpoint_info("charge_card")
    assert info is not None
    assert info["file"] == "payments.py"
    assert "checkout_endpoint" in info["called_by"]
    assert set(info["calls"]) >= {"_validate_token", "_submit_to_gateway"}


def test_endpoint_info_returns_none_for_unknown_name():
    graph = build_graph()

    assert graph.get_endpoint_info("does_not_exist") is None


def test_search_issues_matches_title_and_body():
    graph = build_graph()

    matches = graph.search_issues("gateway timeout")
    ids = {m["id"] for m in matches}
    assert 108 in ids


def test_search_issues_returns_empty_for_no_match():
    graph = build_graph()

    assert graph.search_issues("completely unrelated xyz") == []


def test_trace_impact_finds_downstream_and_upstream_hops():
    graph = build_graph()

    result = graph.trace_impact("charge_card", max_hops=2)
    assert result is not None
    assert result["file"] == "payments.py"

    by_name = {item["name"]: item for item in result["affected"]}
    assert by_name["_validate_token"]["relation"] == "calls"
    assert by_name["_validate_token"]["hops"] == 1
    assert by_name["checkout_endpoint"]["relation"] == "called_by"
    assert by_name["checkout_endpoint"]["hops"] == 1


def test_trace_impact_respects_max_hops():
    graph = build_graph()

    shallow = graph.trace_impact("charge_card", max_hops=1)
    deep = graph.trace_impact("charge_card", max_hops=2)
    assert len(shallow["affected"]) <= len(deep["affected"])


def test_trace_impact_returns_none_for_unknown_name():
    graph = build_graph()

    assert graph.trace_impact("does_not_exist") is None


def test_get_test_coverage_finds_covering_test():
    graph = build_graph()

    coverage = graph.get_test_coverage("charge_card")
    assert coverage is not None
    names = {c["test"] for c in coverage["covered_by"]}
    assert "test_charge_card_returns_charged_amount" in names


def test_get_test_coverage_reports_gap_for_uncovered_function():
    graph = build_graph()

    coverage = graph.get_test_coverage("checkout_endpoint")
    assert coverage is not None
    assert coverage["covered_by"] == []


def test_get_test_coverage_returns_none_for_unknown_name():
    graph = build_graph()

    assert graph.get_test_coverage("does_not_exist") is None
