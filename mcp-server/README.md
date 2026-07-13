# mcp-server

Python MCP server. Builds the project graph (tree-sitter code parsing + GitHub Issues ingestion) and exposes it as MCP tools: `get_endpoint_info`, `trace_impact`, `get_test_coverage`, `search_issues`.

Not yet scaffolded — see [docs/architecture.md](../docs/architecture.md). Week 1 target: parse a target repo into a graph, stand up `get_endpoint_info` and `search_issues` over the MCP Python SDK.
