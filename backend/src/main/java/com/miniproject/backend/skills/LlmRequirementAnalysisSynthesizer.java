package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Evidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Primary
@Component
@ConditionalOnProperty(name = "analysis.requirement.provider", havingValue = "llm")
public class LlmRequirementAnalysisSynthesizer implements RequirementAnalysisSynthesizer {

    private static final String SYSTEM_PROMPT = """
            You are a Software Analyst assistant. Analyse a ticket or change request for business rules,
            business value, scope boundary, risks, ambiguities, missing information, assumptions,
            likely affected functional areas, and analyst concerns.
            Pay attention to actor clarity, contradictory acceptance criteria, privacy, security, role-based access,
            performance, availability, audit/compliance, and testability risks.
            Return strict JSON only. Do not include markdown.
            """;

    private final AiAnalysisClient aiAnalysisClient;
    private final RuleBasedRequirementAnalysisSynthesizer fallback;
    private final ObjectMapper objectMapper;

    public LlmRequirementAnalysisSynthesizer(
            AiAnalysisClient aiAnalysisClient,
            RuleBasedRequirementAnalysisSynthesizer fallback,
            ObjectMapper objectMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequirementAnalysisResult synthesize(String description, List<String> sentences, List<String> candidateAreas) {
        String prompt = buildPrompt(description, candidateAreas);
        return aiAnalysisClient.analyze(SYSTEM_PROMPT, prompt)
                .flatMap(this::parseResult)
                .orElseGet(() -> fallback.synthesize(description, sentences, candidateAreas));
    }

    private String buildPrompt(String description, List<String> candidateAreas) {
        return """
                Analyse this Software Analyst ticket/change request.

                Requirement text:
                %s

                Candidate scope clues extracted by the platform:
                %s

                Analyse it like a real Software Analyst preparing a ticket for development and QA:
                - Identify the business driver/value in plain language.
                - Define in-scope behavior, out-of-scope boundaries, and delivery dependencies.
                - Classify project risk/priority using Critical/High/P1/P2/P3 style triage.
                - Check whether actor/role and expected business outcome are clear.
                - Flag contradictions between title, description, acceptance criteria, and comments.
                - Flag PII, location, donor/victim, payment, or sensitive-data exposure.
                - Flag role-based access/security concerns.
                - Flag performance/availability risk for filtering, searching, listing, reporting, retry, or bulk actions.
                - Flag audit, compliance, logging, and traceability concerns when relevant.
                - Flag testing concerns that should shape QA/UAT scope.

                Return this JSON shape exactly:
                {
                  "business_rules": ["confirmed rules or expected behavior"],
                  "business_value": "why this change matters to the business, users, operation, compliance, or delivery outcome",
                  "scope_boundary": {
                    "in_scope": ["confirmed behavior included in this change"],
                    "out_of_scope": ["explicit or inferred boundaries not included unless separately approved"],
                    "dependencies": ["stakeholder, developer, QA, data, process, or external dependency"]
                  },
                  "ambiguities": [{"note": "unclear point", "evidence": "text that caused it"}],
                  "missing_information": ["information the analyst should ask for"],
                  "assumptions": ["reasonable assumptions if the team continues"],
                  "analyst_concerns": [
                    {
                      "category": "privacy|security|role_access|performance|availability|compliance|audit|data_quality|testing|none",
                      "severity": "low|medium|high",
                      "note": "why this matters to the analyst workflow",
                      "evidence": "ticket text or clue that caused this concern",
                      "question": "follow-up question or validation step"
                    }
                  ],
                  "project_risks": [
                    {
                      "priority": "Critical|High|P1|P2|P3",
                      "severity": "critical|high|medium|low",
                      "area": "business_value|scope|stakeholder|change_control|privacy|security|role_access|performance|testing|timeline|resource|delivery",
                      "reason": "why this risk matters",
                      "mitigation": "what the analyst should do next",
                      "owner": "role responsible for resolving it"
                    }
                  ],
                  "potential_affected_areas": ["user-facing area, module, page, API, database entity, or workflow"],
                  "confidence": "low|medium|high"
                }
                """.formatted(description, candidateAreas);
    }

    private java.util.Optional<RequirementAnalysisResult> parseResult(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            List<String> businessRules = stringList(root.path("business_rules"));
            String businessValue = root.path("business_value").asText("").trim();
            RequirementAnalysisResult.ScopeBoundary scopeBoundary = scopeBoundary(root.path("scope_boundary"));
            List<RequirementAnalysisResult.Ambiguity> ambiguities = ambiguityList(root.path("ambiguities"));
            List<String> missingInformation = stringList(root.path("missing_information"));
            List<String> assumptions = stringList(root.path("assumptions"));
            List<RequirementAnalysisResult.AnalystConcern> analystConcerns = analystConcernList(root.path("analyst_concerns"));
            List<RequirementAnalysisResult.ProjectRisk> projectRisks = projectRiskList(root.path("project_risks"));
            List<String> potentialAffectedAreas = stringList(root.path("potential_affected_areas"));
            String confidence = normalizeConfidence(root.path("confidence").asText("low"));

            List<Evidence> evidence = new ArrayList<>();
            for (String rule : businessRules) {
                evidence.add(new Evidence(rule, "AI requirement analysis"));
            }

            return java.util.Optional.of(new RequirementAnalysisResult(
                    businessRules,
                    businessValue,
                    scopeBoundary,
                    ambiguities,
                    missingInformation,
                    assumptions,
                    analystConcerns,
                    projectRisks,
                    potentialAffectedAreas,
                    confidence,
                    evidence,
                    List.of()));
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
    }

    private String stripCodeFence(String rawJson) {
        String trimmed = rawJson.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return trimmed.substring(firstLineEnd + 1, lastFence).trim();
        }
        return trimmed;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private List<RequirementAnalysisResult.Ambiguity> ambiguityList(JsonNode node) {
        List<RequirementAnalysisResult.Ambiguity> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String note = item.path("note").asText("").trim();
            String evidence = item.path("evidence").asText("").trim();
            if (!note.isBlank()) {
                values.add(new RequirementAnalysisResult.Ambiguity(note, evidence));
            }
        }
        return values;
    }

    private RequirementAnalysisResult.ScopeBoundary scopeBoundary(JsonNode node) {
        return new RequirementAnalysisResult.ScopeBoundary(
                stringList(node.path("in_scope")),
                stringList(node.path("out_of_scope")),
                stringList(node.path("dependencies")));
    }

    private List<RequirementAnalysisResult.AnalystConcern> analystConcernList(JsonNode node) {
        List<RequirementAnalysisResult.AnalystConcern> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String category = item.path("category").asText("").trim();
            String severity = normalizeSeverity(item.path("severity").asText("low"));
            String note = item.path("note").asText("").trim();
            String evidence = item.path("evidence").asText("").trim();
            String question = item.path("question").asText("").trim();
            if (!category.isBlank() && !note.isBlank()) {
                values.add(new RequirementAnalysisResult.AnalystConcern(category, severity, note, evidence, question));
            }
        }
        return values;
    }

    private List<RequirementAnalysisResult.ProjectRisk> projectRiskList(JsonNode node) {
        List<RequirementAnalysisResult.ProjectRisk> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String priority = normalizePriority(item.path("priority").asText("P3"));
            String severity = normalizeRiskSeverity(item.path("severity").asText("low"));
            String area = item.path("area").asText("").trim();
            String reason = item.path("reason").asText("").trim();
            String mitigation = item.path("mitigation").asText("").trim();
            String owner = item.path("owner").asText("").trim();
            if (!area.isBlank() && !reason.isBlank()) {
                values.add(new RequirementAnalysisResult.ProjectRisk(priority, severity, area, reason, mitigation, owner));
            }
        }
        return values;
    }

    private String normalizeConfidence(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "medium", "high" -> normalized;
            default -> "low";
        };
    }

    private String normalizeSeverity(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "medium", "high" -> normalized;
            default -> "low";
        };
    }

    private String normalizeRiskSeverity(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "critical", "high", "medium" -> normalized;
            default -> "low";
        };
    }

    private String normalizePriority(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "CRITICAL", "HIGH", "P1", "P2", "P3" -> normalized;
            default -> "P3";
        };
    }
}
