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
            ambiguities, missing information, assumptions, and likely affected functional areas.
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

                Return this JSON shape exactly:
                {
                  "business_rules": ["confirmed rules or expected behavior"],
                  "ambiguities": [{"note": "unclear point", "evidence": "text that caused it"}],
                  "missing_information": ["information the analyst should ask for"],
                  "assumptions": ["reasonable assumptions if the team continues"],
                  "potential_affected_areas": ["user-facing area, module, page, API, database entity, or workflow"],
                  "confidence": "low|medium|high"
                }
                """.formatted(description, candidateAreas);
    }

    private java.util.Optional<RequirementAnalysisResult> parseResult(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            List<String> businessRules = stringList(root.path("business_rules"));
            List<RequirementAnalysisResult.Ambiguity> ambiguities = ambiguityList(root.path("ambiguities"));
            List<String> missingInformation = stringList(root.path("missing_information"));
            List<String> assumptions = stringList(root.path("assumptions"));
            List<String> potentialAffectedAreas = stringList(root.path("potential_affected_areas"));
            String confidence = normalizeConfidence(root.path("confidence").asText("low"));

            List<Evidence> evidence = new ArrayList<>();
            for (String rule : businessRules) {
                evidence.add(new Evidence(rule, "AI requirement analysis"));
            }

            return java.util.Optional.of(new RequirementAnalysisResult(
                    businessRules,
                    ambiguities,
                    missingInformation,
                    assumptions,
                    potentialAffectedAreas,
                    confidence,
                    evidence));
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

    private String normalizeConfidence(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "medium", "high" -> normalized;
            default -> "low";
        };
    }
}
