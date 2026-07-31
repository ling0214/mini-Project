package com.miniproject.backend.mcp;

import java.util.List;
import java.util.Map;

/**
 * Grounds skills in the project graph exposed by mcp-server. Kept as an
 * interface (not just the MCP client concrete class) so skills can be unit
 * tested with a mock instead of spawning the Python process every run.
 */
public interface ProjectGraphClient {

    Map<String, Object> getEndpointInfo(String name);

    Map<String, Object> searchIssues(String query);

    Map<String, Object> traceImpact(String name, int maxHops);

    Map<String, Object> getTestCoverage(String name);

    Map<String, Object> searchProjectContext(String project, String query, int limit);

    /** Builds the codebase-memory-mcp graph for repoPath under `name` — without this, searchProjectContext finds nothing for a newly declared project. */
    Map<String, Object> indexProject(String repoPath, String name);

    Map<String, Object> projectIndexStatus(String project);

    /** High-level architecture (packages/layers/boundaries) — feeds the fast-onboarding diagram. */
    Map<String, Object> getArchitecture(String project, List<String> aspects);
}
