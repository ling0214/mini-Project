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
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(name = "analysis.hermes-setup.provider", havingValue = "llm")
public class LlmHermesSetupWizardSynthesizer implements HermesSetupWizardSynthesizer {

    private static final String SYSTEM_PROMPT = """
            You are helping an analyst stand up a new Hermes (a self-hosted, multi-platform incident-response
            AI agent gateway) deployment for a specific repo. You are given the analyst's real answers to a
            setup questionnaire (repo path, platforms, channel/account identifiers, allowed senders, storage
            directories, PR-package settings). Fill every real answer directly into the generated YAML as-is.
            Only use an obvious placeholder like <FILL_IN_...> for genuine secrets (bot tokens, passwords,
            git credentials) that were never asked for and must never be invented. Return strict JSON only.
            Do not include markdown.
            """;

    private final AiAnalysisClient aiAnalysisClient;
    private final RuleBasedHermesSetupWizardSynthesizer fallback;
    private final ObjectMapper objectMapper;

    public LlmHermesSetupWizardSynthesizer(
            AiAnalysisClient aiAnalysisClient,
            RuleBasedHermesSetupWizardSynthesizer fallback,
            ObjectMapper objectMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public HermesSetupWizardResult synthesize(HermesSetupWizardAnswers answers) {
        String prompt = buildPrompt(answers);
        return aiAnalysisClient.analyze(SYSTEM_PROMPT, prompt)
                .flatMap(raw -> parseResult(raw, answers))
                .orElseGet(() -> fallback.synthesize(answers));
    }

    private String buildPrompt(HermesSetupWizardAnswers answers) {
        return """
                Repo path: %s
                Platforms: %s
                Discord intake channel id: %s
                Email IMAP host: %s
                Email account: %s
                Email allowed senders: %s
                Incident reports directory: %s
                Incident extracts directory: %s
                Incident downloads directory: %s
                Server log path: %s
                PR-package flow enabled: %s
                Git host: %s

                Return this JSON shape exactly:
                {
                  "generated_yaml": "the full YAML skeleton as one string, with every real answer above filled in as-is and only genuine secrets left as <FILL_IN_...> placeholders",
                  "checklist": ["what the analyst must still fill in or verify before this config is usable"],
                  "notes": ["caveats, assumptions, or things this generation could not determine"]
                }
                """.formatted(
                answers.repoPath(),
                answers.platforms(),
                blankToNone(answers.discordChannelId()),
                blankToNone(answers.emailImapHost()),
                blankToNone(answers.emailAccount()),
                answers.emailAllowedSenders(),
                blankToNone(answers.incidentReportsDir()),
                blankToNone(answers.incidentExtractsDir()),
                blankToNone(answers.incidentDownloadsDir()),
                blankToNone(answers.serverLogPath()),
                answers.prPackageEnabled(),
                blankToNone(answers.gitHost()));
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "(not given)" : value;
    }

    private Optional<HermesSetupWizardResult> parseResult(String rawJson, HermesSetupWizardAnswers answers) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            String generatedYaml = root.path("generated_yaml").asText("").trim();
            List<String> checklist = stringList(root.path("checklist"));
            List<String> notes = stringList(root.path("notes"));
            if (generatedYaml.isBlank() || checklist.isEmpty()) {
                return Optional.empty();
            }
            List<Evidence> evidence = List.of(
                    new Evidence("AI-generated setup skeleton for " + answers.repoPath(), "hermes-setup-wizard (LLM)"));
            return Optional.of(new HermesSetupWizardResult(answers, generatedYaml, checklist, notes, evidence));
        } catch (IOException e) {
            return Optional.empty();
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
}
