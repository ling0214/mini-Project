package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import com.miniproject.backend.workspace.ProjectWorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skills/code-qa.md, Week 1 shape: deterministic tool calls (this class
 * decides what to fetch), synthesis only is delegated to
 * {@link AnswerSynthesizer}. See docs/architecture.md: the coordinator/skill
 * layer stays deterministic until an LLM planner is deliberately added.
 */
@Component
public class CodeQaSkill {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "what", "does", "the", "depend", "dependent", "dependency", "dependencies",
            "change", "changes", "changing", "breaks", "break", "affect", "affects", "affected",
            "and", "for", "with", "this", "that", "from", "into", "did", "who", "calls", "call");

    private final ProjectGraphClient graphClient;
    private final AnswerSynthesizer synthesizer;
    private final ProjectWorkspaceRepository workspaceRepository;
    private final String fallbackProjectName;

    @Autowired
    public CodeQaSkill(
            ProjectGraphClient graphClient,
            AnswerSynthesizer synthesizer,
            ProjectWorkspaceRepository workspaceRepository,
            @Value("${analysis.target-project.name:MyBanjirCare}") String fallbackProjectName) {
        this.graphClient = graphClient;
        this.synthesizer = synthesizer;
        this.workspaceRepository = workspaceRepository;
        this.fallbackProjectName = fallbackProjectName;
    }

    public CodeQaSkill(ProjectGraphClient graphClient, AnswerSynthesizer synthesizer) {
        this(graphClient, synthesizer, null, "MyBanjirCare");
    }

    public CodeQaResult run(String question) {
        List<Map<String, Object>> resolvedEndpoints = extractCandidates(question).stream()
                .map(graphClient::getEndpointInfo)
                .filter(info -> Boolean.TRUE.equals(info.get("found")))
                .toList();

        Map<String, Object> issueSearch = new HashMap<>(graphClient.searchIssues(question));
        Map<String, Object> projectContext = searchActiveProjectContext(question);
        if (!projectContext.isEmpty()) {
            issueSearch.put("project_context", projectContext);
        }

        return synthesizer.synthesize(question, resolvedEndpoints, issueSearch);
    }

    private Map<String, Object> searchActiveProjectContext(String question) {
        String project = activeProjectName().orElse(fallbackProjectName);
        if (project == null || project.isBlank()) {
            return Map.of();
        }
        try {
            return graphClient.searchProjectContext(project, question, 8);
        } catch (RuntimeException e) {
            return Map.of("project", project, "matches", List.of(), "count", 0, "error", e.getMessage());
        }
    }

    private Optional<String> activeProjectName() {
        if (workspaceRepository == null) {
            return Optional.empty();
        }
        return workspaceRepository.findByActiveTrue().map(workspace -> workspace.getName());
    }

    private Set<String> extractCandidates(String question) {
        Matcher matcher = IDENTIFIER.matcher(question);
        Set<String> candidates = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token.toLowerCase(Locale.ROOT))) {
                candidates.add(token);
            }
        }
        return candidates;
    }
}
