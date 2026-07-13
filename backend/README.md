# backend

Spring Boot harness: role/profile routing, skill orchestration, human-review gate, REST API for the frontend. Acts as an MCP **client** to `mcp-server/`.

Not yet scaffolded — see [docs/architecture.md](../docs/architecture.md) for the design this implements. Week 1 target: a `code-qa` endpoint that calls the MCP server's `get_endpoint_info` / `search_issues` tools and returns a grounded answer.
