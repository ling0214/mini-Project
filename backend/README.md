# backend

Spring Boot harness: deterministic profile/skill routing, the human-review gate, and the REST API for the frontend. Talks to `mcp-server` as a real **MCP client** over stdio (official `io.modelcontextprotocol.sdk` Java SDK) — not a bespoke HTTP wrapper.

See [docs/architecture.md](../docs/architecture.md) for the full design.

## Status

Week 1 slice implemented end to end and verified live: `POST /api/skills/code-qa` → `CoordinatorService` (profile/skill gating) → `CodeQaSkill` (deterministic MCP tool calls) → `mcp-server`'s real `get_endpoint_info` / `search_issues` tools → `RuleBasedAnswerSynthesizer` → an `artifact.v1` envelope with `reviewed: false`.

The synthesis step is intentionally behind an `AnswerSynthesizer` interface. `RuleBasedAnswerSynthesizer` (the current default) assembles the answer purely from graph/issue facts, no LLM call — so the whole path is runnable and testable with zero secrets. A `ClaudeAnswerSynthesizer` implementing the same interface is the planned next step once an Anthropic API key is available to verify it against.

## Prerequisites

`mcp-server`'s virtualenv must exist first (see [../mcp-server/README.md](../mcp-server/README.md)) — this backend spawns `../mcp-server/.venv/Scripts/python.exe -m mcp_server.server` as a subprocess on startup.

## Run

```bash
mvn spring-boot:run
```

Starts on `:8080`. Startup log should show the MCP handshake:

```
i.m.c.transport.StdioClientTransport  : MCP server started
i.m.client.LifecycleInitializer       : Server response with Protocol: 2024-11-05 ... Info: Implementation[name=mini-project-graph...]
```

## Try it

```bash
curl -s -X POST http://localhost:8080/api/skills/code-qa \
  -H "Content-Type: application/json" \
  -d '{"profile":"project-analyst","question":"If we change charge_card, what breaks?"}'
```

Returns an `artifact.v1` envelope citing `payments.py:1` and `issue #108` from the real graph — not a paraphrase.

## Test

```bash
mvn test
```

7 tests: unit tests for `CodeQaSkill`/`RuleBasedAnswerSynthesizer` against a mocked `ProjectGraphClient`, plus `McpToolClientIntegrationTest` — which spawns the actual Python `mcp-server` and asserts on real call-graph facts. That integration test is the one that matters most here: it's what proves the Java harness and the Python graph server genuinely interoperate over MCP, not just that each half compiles.

## Config (`src/main/resources/application.yml`)

| Property | Default | Notes |
|---|---|---|
| `mcp.python-executable` | `../mcp-server/.venv/Scripts/python.exe` | Relative to backend's working directory. Override with an absolute path if running from elsewhere. |
| `mcp.server-args` | `-m,mcp_server.server` | Passed to the python executable. |

## Known limitations (MVP, called out on purpose)

- `CoordinatorService`'s profile → allowed-skills map is hardcoded in Java, not parsed from `profiles/*.md` — the markdown specs and the code can drift. Fine while there are only two profiles; worth generating from one source if a third profile is added.
- Only `code-qa` is wired up. `impact-analysis`, `test-case-gen`, `weekly-report` have skill specs in `../skills/` but no backend implementation yet (Week 2–4).
- No auth — this is a local dev harness, not exposed anywhere.
