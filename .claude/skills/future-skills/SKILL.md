---
name: future-skills
description: A working lens, not a task runner — apply it whenever planning, implementing, or reviewing non-trivial work in this repo so the approach actively exercises the five competencies the World Economic Forum's Future of Jobs research flags as already critical and growing more so — AI & big data, analytical thinking, creative thinking, resilience/flexibility/agility, and technological literacy. Trigger on "/future-skills", on requests to design/review approach quality, or self-invoke before starting multi-step or ambiguous work in this repo. Not for one-off trivial edits (a typo fix, a one-line config change) — the lens adds friction that isn't worth it there.
---

# /future-skills

Five competencies, each with what "applying it" concretely means while working in this repo — not abstract career advice, but checkable behaviors for a coding session.

## 1. AI and big data

Use the tools that turn "search the codebase" into "query a structured model of it," rather than defaulting to ad-hoc grep first.

- Before broad exploration, reach for `codebase-memory-mcp` (`search_graph`, `trace_path`, `get_architecture`, `query_graph`) — see the codebase-memory skill and the SessionStart hook's Code Discovery Protocol. Grep/Glob/Read are still right for text, configs, and non-code files, or once the graph tool has narrowed the target.
- When reasoning about impact ("what breaks if I change this?"), prefer `trace_path`/`trace_impact`-style data over guessing from naming conventions.
- Known gap in this repo: the indexer doesn't parse JS/JSX (`.claude/skills/develop/SKILL.md`, Step 5) — don't trust a "clean" graph query to mean a frontend change is covered; fall back to Grep/Read there deliberately, not by accident.

## 2. Analytical thinking

Diagnose root cause before patching symptoms; verify claims instead of asserting them.

- When something fails (test, build, runtime), trace it to the actual cause before editing — don't paper over a failing assertion or silence an error.
- Treat "should work" as insufficient. Run the verification gate for whatever subproject was touched (`mvn test` for backend, `pytest` for mcp-server, `npm run build` for frontend — see `.claude/skills/develop/SKILL.md` Step 4) and report actual outcomes.
- When a decision has real tradeoffs (schema shape, sync vs. async, where a field lives), name the tradeoff explicitly rather than picking silently.

## 3. Creative thinking

Before implementing the first workable approach, briefly consider whether it's the most reuse-friendly one — this repo already has an established Artifact/Coordinator/Agent pattern (`.claude/skills/develop/SKILL.md`) that most new work should extend rather than reinvent.

- For genuinely new capability (new skill, new UI shape), ask "does an existing pattern already fit this, possibly with a small extension?" before adding a new one.
- When stuck or blocked, generate more than one option before asking the user to choose, rather than presenting a single default as the only path.

## 4. Resilience, flexibility, and agility

Treat ambiguity and setbacks as normal inputs, not stopping points.

- If a first approach fails (hung MCP session, broken build, unexpected test failure), adapt — this repo has documented, hard-won examples worth reusing as precedent (e.g. the `cbmm_client.py` persistent-session hang, `.claude/skills/develop/SKILL.md` mcp-server section) rather than rediscovering the same dead end.
- When requirements are underspecified, don't stall silently or guess destructively — ask a targeted clarifying question (per this session's Auto Mode guidance) or make the reversible, well-reasoned call and say so.
- Re-plan when new information contradicts the original plan, instead of forcing the original plan through.

## 5. Technological literacy

Use each subproject's actual stack correctly rather than a generic or outdated pattern.

- Backend: Spring Boot conventions already established (Artifact/Coordinator/Agent seam, snake_case JSON via the global Jackson naming strategy, JUnit 5 + AssertJ) — see `.claude/skills/develop/SKILL.md`.
- Frontend: the existing single-file React conventions (`api()` helper, established presentational components, `styles.css` class vocabulary) — don't introduce a parallel pattern (e.g. raw `fetch`, a new CSS-in-JS approach) without reason.
- mcp-server: Python/MCP-over-stdio conventions, including the one-shot-subprocess workaround for `codebase-memory-mcp` — a "literate" fix respects why that workaround exists instead of "simplifying" it back into a known hang.
- When a tool or library choice is unclear, check what's already a dependency in this repo (`pom.xml`, `package.json`, `requirements`/`pyproject`) before adding a new one.

## How to use this in a session

This isn't a procedure with an output artifact — it's a checklist to run silently against whatever task is already in flight. A quick self-check before declaring non-trivial work done:

1. Did I use structured code intelligence where it beat blind search? (AI & big data)
2. Did I verify instead of assert, and name real tradeoffs? (Analytical thinking)
3. Did I check for an existing pattern to extend before inventing one? (Creative thinking)
4. Did I adapt cleanly when something didn't go as planned, instead of stalling or forcing it? (Resilience/flexibility/agility)
5. Did the implementation actually match this repo's real stack conventions? (Technological literacy)

If a task is trivial (typo, one-line config), skip this — the lens is for work substantial enough that the extra half-second of reflection pays for itself.
