package com.miniproject.backend.skills;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SkillScannerService {

    /**
     * Run mechanical scan on a skill (Java source code).
     * Returns scan findings. Does NOT make the decision to approve/reject—human does that.
     */
    public SkillScanResult scanSkill(String skillName, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            List<ScanFinding> unresolvedFindings = List.of(new ScanFinding(
                    "Source code unavailable",
                    "(no source provided)",
                    ScanSeverity.MEDIUM,
                    "Scanner had no source to inspect, so nothing was checked. Do not treat this as a clean scan — "
                            + "locate the skill's source and re-scan, or review manually before approving."));
            return new SkillScanResult(skillName, ScanType.MECHANICAL, ScanStatus.REVIEW_NEEDED, unresolvedFindings);
        }

        List<ScanFinding> findings = new ArrayList<>();

        // Semgrep-like patterns (simplified for skeleton)
        findings.addAll(scanForSecrets(sourceCode));
        findings.addAll(scanForRuntimeFetching(sourceCode));
        findings.addAll(scanForSuspiciousNetworkCalls(sourceCode));

        SkillScanResult result = new SkillScanResult(
            skillName,
            ScanType.MECHANICAL,
            findings.isEmpty() ? ScanStatus.PASS : ScanStatus.REVIEW_NEEDED,
            findings
        );

        return result;
    }

    private List<ScanFinding> scanForSecrets(String code) {
        List<ScanFinding> findings = new ArrayList<>();

        if (code == null || code.isEmpty()) {
            return findings;
        }

        // Regex patterns for common secret patterns
        String[] secretPatterns = {
            "password\\s*=\\s*['\"].*['\"]",
            "api[_-]?key\\s*=\\s*['\"].*['\"]",
            "secret\\s*=\\s*['\"].*['\"]",
            "token\\s*=\\s*['\"].*['\"]"
        };

        for (String pattern : secretPatterns) {
            if (code.matches("(?i).*" + pattern + ".*")) {
                findings.add(new ScanFinding(
                    "Potential hardcoded secret",
                    pattern,
                    ScanSeverity.HIGH,
                    "File contains pattern matching hardcoded credentials. Verify in context; may be legitimate if only in tests or docs."
                ));
            }
        }

        return findings;
    }

    private List<ScanFinding> scanForRuntimeFetching(String code) {
        List<ScanFinding> findings = new ArrayList<>();

        if (code == null || code.isEmpty()) {
            return findings;
        }

        // Check for dynamic instruction fetching
        if (code.contains("fetchInstructionsAt") || code.contains("loadRulesFrom(\"http") ||
            code.contains("eval(") || code.contains("Runtime.getRuntime().exec()")) {
            findings.add(new ScanFinding(
                "Runtime instruction/code fetching detected",
                "fetchInstructionsAt|loadRulesFrom|eval|Runtime.getRuntime().exec()",
                ScanSeverity.HIGH,
                "Skill loads instructions or executes code at runtime, making audit impossible. Red flag unless explicitly documented."
            ));
        }

        return findings;
    }

    private List<ScanFinding> scanForSuspiciousNetworkCalls(String code) {
        List<ScanFinding> findings = new ArrayList<>();

        if (code == null || code.isEmpty()) {
            return findings;
        }

        // Check for unusual network patterns
        if (code.contains("http") && (code.contains("openConnection") || code.contains("HttpClient")) &&
            !code.contains("localhost") && !code.contains("127.0.0.1")) {
            findings.add(new ScanFinding(
                "Network call to external host",
                "http|https request outside localhost",
                ScanSeverity.MEDIUM,
                "Skill makes network requests. Verify destination and purpose. Skills should only call internal APIs or documented external services."
            ));
        }

        return findings;
    }

    // Result classes
    public static class SkillScanResult {
        public String skillName;
        public ScanType scanType;
        public ScanStatus status;
        public List<ScanFinding> findings;
        public LocalDateTime scannedAt;

        public SkillScanResult(String skillName, ScanType scanType, ScanStatus status, List<ScanFinding> findings) {
            this.skillName = skillName;
            this.scanType = scanType;
            this.status = status;
            this.findings = findings;
            this.scannedAt = LocalDateTime.now();
        }
    }

    public static class ScanFinding {
        public String title;
        public String pattern;
        public ScanSeverity severity;
        public String recommendation;

        public ScanFinding(String title, String pattern, ScanSeverity severity, String recommendation) {
            this.title = title;
            this.pattern = pattern;
            this.severity = severity;
            this.recommendation = recommendation;
        }
    }

    public enum ScanType {
        MECHANICAL,
        SECURITY,
        PERFORMANCE,
        INTEGRATION
    }

    public enum ScanStatus {
        PASS,
        REVIEW_NEEDED,
        FAIL
    }

    public enum ScanSeverity {
        INFO,
        MEDIUM,
        HIGH
    }
}
