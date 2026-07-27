"""Builds a project graph (functions + call edges + issues) from a target
directory of Python source files, using ast — no execution of target code.

This is the Week 1 slice of the graph described in docs/architecture.md:
functions and calls only. Cross-file import resolution, classes/methods,
and non-Python languages are out of scope until the demo repo is picked.
"""

import ast
import json
import re
from dataclasses import dataclass, field
from pathlib import Path

_WORD = re.compile(r"[a-zA-Z_][a-zA-Z0-9_]*")
_STOPWORDS = {
    "what", "does", "do", "did", "the", "a", "an", "is", "are", "was", "were",
    "if", "we", "change", "changes", "changing", "break", "breaks", "broken",
    "and", "or", "for", "with", "this", "that", "from", "into", "on", "to",
    "of", "in", "not", "no", "all", "any",
}


@dataclass
class FunctionNode:
    name: str
    file: str
    lineno: int
    calls: list[str] = field(default_factory=list)


class _CallCollector(ast.NodeVisitor):
    def __init__(self):
        self.calls: list[str] = []

    def visit_Call(self, node: ast.Call) -> None:
        func = node.func
        if isinstance(func, ast.Name):
            self.calls.append(func.id)
        elif isinstance(func, ast.Attribute):
            self.calls.append(func.attr)
        self.generic_visit(node)


class ProjectGraph:
    """In-memory graph: function name -> FunctionNode, plus a loaded issue list.

    Function names are assumed unique across the target for this MVP slice —
    good enough for a small demo repo, called out explicitly rather than
    silently wrong for real multi-module projects with name collisions.
    """

    def __init__(self):
        self.functions: dict[str, FunctionNode] = {}
        self.issues: list[dict] = []

    @classmethod
    def build(cls, source_dir: str | Path, issues_path: str | Path | None = None) -> "ProjectGraph":
        graph = cls()
        source_dir = Path(source_dir)
        for py_file in sorted(source_dir.rglob("*.py")):
            graph._ingest_file(py_file, source_dir)
        if issues_path is not None and Path(issues_path).exists():
            graph.issues = json.loads(Path(issues_path).read_text(encoding="utf-8"))
        return graph

    def _ingest_file(self, py_file: Path, source_dir: Path) -> None:
        tree = ast.parse(py_file.read_text(encoding="utf-8"), filename=str(py_file))
        rel_path = py_file.relative_to(source_dir).as_posix()
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef):
                collector = _CallCollector()
                collector.visit(node)
                self.functions[node.name] = FunctionNode(
                    name=node.name,
                    file=rel_path,
                    lineno=node.lineno,
                    calls=collector.calls,
                )

    def callers_of(self, name: str) -> list[str]:
        return [fn.name for fn in self.functions.values() if name in fn.calls]

    def get_endpoint_info(self, name: str) -> dict | None:
        fn = self.functions.get(name)
        if fn is None:
            return None
        return {
            "name": fn.name,
            "file": fn.file,
            "line": fn.lineno,
            "calls": [c for c in fn.calls if c in self.functions],
            "called_by": self.callers_of(name),
        }

    def trace_impact(self, name: str, max_hops: int = 2) -> dict | None:
        """Blast radius from `name`: everything it transitively calls
        (relation="calls") and everything that transitively calls it
        (relation="called_by"), out to max_hops in each direction.

        Each affected function is reported once, at the hop count where it
        was first reached (BFS, not DFS) — a function reachable both as a
        caller and a callee only appears under whichever direction found it
        first.
        """
        origin = self.functions.get(name)
        if origin is None:
            return None

        visited = {name}
        affected: list[dict] = []

        for direction in ("calls", "called_by"):
            frontier = [name]
            hop = 0
            while frontier and hop < max_hops:
                hop += 1
                next_frontier = []
                for current in frontier:
                    if direction == "calls":
                        neighbors = [c for c in self.functions[current].calls if c in self.functions]
                    else:
                        neighbors = self.callers_of(current)
                    for neighbor in neighbors:
                        if neighbor in visited:
                            continue
                        visited.add(neighbor)
                        fn = self.functions[neighbor]
                        affected.append({
                            "name": neighbor,
                            "file": fn.file,
                            "line": fn.lineno,
                            "hops": hop,
                            "relation": direction,
                        })
                        next_frontier.append(neighbor)
                frontier = next_frontier

        return {
            "name": origin.name,
            "file": origin.file,
            "line": origin.lineno,
            "affected": affected,
        }

    def get_test_coverage(self, name: str) -> dict | None:
        """Which test_* functions in the graph call `name` directly.

        Reuses the same call-graph data built for get_endpoint_info/
        trace_impact — a test function is just another FunctionNode whose
        name happens to start with "test", so "is this covered" is "does
        any test_* node's calls list include this name", no separate
        scanning pass. Only catches direct calls (syntactic, not
        type-resolved) — same limitation as the rest of this MVP graph.
        """
        if name not in self.functions:
            return None
        covered_by = [
            {"test": fn.name, "file": fn.file, "line": fn.lineno}
            for fn in self.functions.values()
            if fn.name.startswith("test") and name in fn.calls
        ]
        return {"name": name, "covered_by": covered_by}

    def search_issues(self, query: str) -> list[dict]:
        terms = {
            w.lower() for w in _WORD.findall(query)
            if w.lower() not in _STOPWORDS and len(w) >= 3
        }
        if not terms:
            return []
        matches = []
        for issue in self.issues:
            haystack_words = {
                w.lower() for w in _WORD.findall(f"{issue.get('title', '')} {issue.get('body', '')}")
            }
            if terms & haystack_words:
                matches.append(issue)
        return matches
