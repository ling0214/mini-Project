package com.miniproject.backend.github;

import com.miniproject.backend.skills.ImpactAnalysisResult.AffectedModule;
import com.miniproject.backend.skills.ImpactAnalysisResult.ScopeCreepFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a GitHub PR's actually-changed files against the affected
 * modules ImpactAnalysisSkill declared for the same change request (see
 * CoordinatorService.impactAnalysisFromPr) and flags mismatches. A file the
 * PR touched but nothing declared is "undeclared_change"; a module declared
 * but never touched by the PR is "declared_untouched".
 *
 * Path matching is necessarily fuzzy: AffectedModule.path comes from
 * whichever root the codebase graph was indexed against (a workspace's
 * local_path or a Graphify subfolder override), while GitHubPrReader's
 * filenames are relative to the actual GitHub repo root -- these are not
 * guaranteed to be the same root. Bidirectional path-segment suffix
 * matching (same approach as HermesStatusService.pathsRelated) bridges
 * that without requiring an exact match, at the cost of not being able to
 * guarantee zero false positives/negatives -- treat findings as
 * best-effort signal, not certainty.
 */
@Component
public class ScopeCreepDetector {

    public List<ScopeCreepFinding> detect(List<GitHubPrReader.PrFile> changedFiles, List<AffectedModule> declaredModules) {
        List<GitHubPrReader.PrFile> files = changedFiles == null ? List.of() : changedFiles;
        List<AffectedModule> declared = declaredModules == null ? List.of() : declaredModules;

        Map<String, AffectedModule> declaredByNormalizedPath = new LinkedHashMap<>();
        for (AffectedModule module : declared) {
            String normalized = normalize(stripLineSuffix(module.path()));
            if (!normalized.isBlank()) {
                declaredByNormalizedPath.putIfAbsent(normalized, module);
            }
        }

        List<ScopeCreepFinding> findings = new ArrayList<>();
        List<String> matchedDeclaredPaths = new ArrayList<>();

        for (GitHubPrReader.PrFile file : files) {
            String changedPath = normalize(file.filename());
            if (changedPath.isBlank()) {
                continue;
            }
            String match = declaredByNormalizedPath.keySet().stream()
                    .filter(declaredPath -> related(declaredPath, changedPath))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                String status = file.status() == null || file.status().isBlank() ? "modified" : file.status();
                findings.add(new ScopeCreepFinding(
                        file.filename(), "undeclared_change",
                        "PR " + status + " this file, but it is not in the declared affected modules."));
            } else {
                matchedDeclaredPaths.add(match);
            }
        }

        for (Map.Entry<String, AffectedModule> entry : declaredByNormalizedPath.entrySet()) {
            if (!matchedDeclaredPaths.contains(entry.getKey())) {
                AffectedModule module = entry.getValue();
                findings.add(new ScopeCreepFinding(
                        module.path(), "declared_untouched",
                        "Declared as affected (" + module.name() + ") but the PR never touched this file."));
            }
        }

        return findings;
    }

    /** True when one path is the other, or one path ends with "/" + the other. */
    private static boolean related(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return a.endsWith("/" + b) || b.endsWith("/" + a);
    }

    private static String normalize(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
    }

    /** AffectedModule.path is "{file}:{line}" -- strip the line suffix if present so only the file path remains. */
    private static String stripLineSuffix(String path) {
        if (path == null) {
            return "";
        }
        int lastColon = path.lastIndexOf(':');
        if (lastColon < 0 || lastColon == path.length() - 1) {
            return path;
        }
        String suffix = path.substring(lastColon + 1);
        return isNumeric(suffix) ? path.substring(0, lastColon) : path;
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
