package com.miniproject.backend.skills;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local project-context retrieval for the current MVP. It reads a configured
 * target repository and ranks relevant files for a ticket/change request.
 *
 * This is intentionally simple keyword retrieval, not full vector RAG yet.
 * The output is the same trace-like shape used by ImpactAnalysisSynthesizer so
 * this can later be replaced by codebase-memory/RAG retrieval behind the same
 * method.
 */
@Component
public class ProjectContextMatcher {

    private static final Pattern WORD = Pattern.compile("[a-zA-Z][a-zA-Z0-9_\\-]{2,}");
    private static final int MAX_FILE_BYTES = 220_000;
    private static final int MAX_MATCHES = 12;
    private static final Set<String> STOPWORDS = Set.of(
            "ticket", "key", "title", "priority", "high", "medium", "low", "critical", "reporter",
            "mbc", "fyp", "supervisor", "owner", "product",
            "description", "acceptance", "criteria", "comments", "notes", "given", "when",
            "then", "should", "could", "would", "must", "able", "allow", "allows", "before",
            "after", "only", "shows", "matching", "records", "page", "update", "change",
            "add", "remove", "filter", "filters", "select", "selects", "help", "need",
            "confirm", "confirmed", "whether", "through", "reload", "ajax", "system", "user",
            "the", "and", "are", "this", "that", "for", "shown", "available");

    private static final List<String> RELEVANT_EXTENSIONS = List.of(
            ".php", ".blade.php", ".js", ".css", ".md", ".sql", ".yml", ".yaml", ".json");

    private final String targetProjectName;
    private final Path targetProjectPath;

    @Autowired
    public ProjectContextMatcher(
            @Value("${analysis.target-project.name:MyBanjirCare}") String targetProjectName,
            @Value("${analysis.target-project.path:C:/tmp/MyBanjirCare}") String targetProjectPath) {
        this.targetProjectName = targetProjectName;
        this.targetProjectPath = Path.of(targetProjectPath);
    }

    ProjectContextMatcher(String targetProjectName, Path targetProjectPath) {
        this.targetProjectName = targetProjectName;
        this.targetProjectPath = targetProjectPath;
    }

    public List<Map<String, Object>> findRelevantTraces(String changeRequest) {
        Set<String> terms = extractTerms(changeRequest);
        if (terms.isEmpty()) {
            return List.of();
        }

        if (Files.isDirectory(targetProjectPath)) {
            List<Map<String, Object>> retrieved = retrieveFromRepository(terms);
            if (!retrieved.isEmpty()) {
                return retrieved;
            }
        }

        return fallbackDemoMatches(changeRequest);
    }

    private List<Map<String, Object>> retrieveFromRepository(Set<String> terms) {
        try (Stream<Path> stream = Files.walk(targetProjectPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isRelevantFile)
                    .map(path -> scoreFile(path, terms))
                    .filter(match -> match.score() > 0)
                    .sorted(Comparator.comparingInt(FileMatch::score).reversed()
                            .thenComparing(match -> match.relativePath().toString()))
                    .limit(MAX_MATCHES)
                    .map(this::toTrace)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isRelevantFile(Path path) {
        Path relative = targetProjectPath.relativize(path);
        String normalized = relative.toString().replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".git/")
                || lower.contains("/.git/")
                || lower.startsWith("vendor/")
                || lower.contains("/vendor/")
                || lower.startsWith("node_modules/")
                || lower.contains("/node_modules/")
                || lower.startsWith("storage/framework/")
                || lower.contains("/storage/framework/")
                || lower.startsWith("bootstrap/cache/")
                || lower.contains("/bootstrap/cache/")
                || lower.startsWith("storage/logs/")
                || lower.contains("/storage/logs/")) {
            return false;
        }
        return RELEVANT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private FileMatch scoreFile(Path path, Set<String> terms) {
        Path relative = targetProjectPath.relativize(path);
        String relativeText = relative.toString().replace('\\', '/');
        String searchablePath = splitCamelCase(relativeText).toLowerCase(Locale.ROOT);
        String content = readFilePrefix(path).toLowerCase(Locale.ROOT);

        int score = 0;
        List<String> hits = new ArrayList<>();
        for (String term : terms) {
            int termScore = 0;
            if (searchablePath.contains(term)) {
                termScore += 7;
            }
            if (content.contains(term)) {
                termScore += 3;
            }
            if (termScore > 0) {
                score += termScore;
                hits.add(term);
            }
        }
        if (!hits.isEmpty()) {
            score += structureBoost(searchablePath);
        }
        return new FileMatch(relative, score, hits);
    }

    private String readFilePrefix(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            int length = Math.min(bytes.length, MAX_FILE_BYTES);
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static int structureBoost(String path) {
        if (path.startsWith("routes/")) {
            return 12;
        }
        if (path.contains("/controllers/")) {
            return 22;
        }
        if (path.contains("/models/")) {
            return 16;
        }
        if (path.contains("/views/")) {
            return 4;
        }
        if (path.contains("/migrations/") || path.contains("/schema/")) {
            return 2;
        }
        return 0;
    }

    private Map<String, Object> toTrace(FileMatch match) {
        String path = match.relativePath().toString().replace('\\', '/');
        String name = moduleName(path);
        String hitText = match.hits().isEmpty() ? "project structure" : String.join(", ", match.hits());
        return Map.of(
                "found", true,
                "name", name,
                "file", path,
                "line", 1,
                "reason", targetProjectName + " project context matched " + hitText + " in " + path,
                "affected", List.of());
    }

    private static String moduleName(String path) {
        String fileName = Path.of(path).getFileName().toString();
        if (fileName.endsWith(".blade.php")) {
            return fileName.substring(0, fileName.length() - ".blade.php".length()) + "View";
        }
        int dot = fileName.indexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static Set<String> extractTerms(String text) {
        String normalized = splitCamelCase(text == null ? "" : text).toLowerCase(Locale.ROOT);
        Matcher matcher = WORD.matcher(normalized);
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = matcher.group().replace("-", " ").trim();
            if (!STOPWORDS.contains(term) && term.length() >= 3) {
                terms.add(term);
            }
        }
        return terms;
    }

    private static String splitCamelCase(String text) {
        return text
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
    }

    private List<Map<String, Object>> fallbackDemoMatches(String changeRequest) {
        String lower = changeRequest == null ? "" : changeRequest.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> traces = new ArrayList<>();
        if (lower.contains("donation") || lower.contains("donor")) {
            traces.add(fallbackTrace("DonationController", "app/Http/Controllers/DonationController.php",
                    "fixed fallback: donation workflow controller"));
            traces.add(fallbackTrace("Donation", "app/Models/Donation.php",
                    "fixed fallback: donation domain model"));
        }
        if (lower.contains("aid request") || lower.contains("urgency") || lower.contains("category")) {
            traces.add(fallbackTrace("AidRequestController", "app/Http/Controllers/AidRequestController.php",
                    "fixed fallback: aid request workflow controller"));
            traces.add(fallbackTrace("AidRequest", "app/Models/AidRequest.php",
                    "fixed fallback: aid request domain model"));
        }
        if (lower.contains("city") || lower.contains("area") || lower.contains("location")) {
            traces.add(fallbackTrace("CityController", "app/Http/Controllers/Api/CityController.php",
                    "fixed fallback: city lookup API"));
        }
        return traces;
    }

    private Map<String, Object> fallbackTrace(String name, String file, String reason) {
        return Map.of(
                "found", true,
                "name", name,
                "file", file,
                "line", 1,
                "reason", targetProjectName + " project context " + reason,
                "affected", List.of());
    }

    private record FileMatch(Path relativePath, int score, List<String> hits) {
    }
}
