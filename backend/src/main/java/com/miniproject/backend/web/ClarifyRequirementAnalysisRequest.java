package com.miniproject.backend.web;

import java.util.ArrayList;
import java.util.List;

public record ClarifyRequirementAnalysisRequest(
        String profile,
        String additionalInfo,
        List<ClarificationAnswer> clarificationAnswers) {

    public String clarificationText() {
        List<String> lines = new ArrayList<>();
        List<ClarificationAnswer> answers = clarificationAnswers == null ? List.of() : clarificationAnswers;
        for (ClarificationAnswer answer : answers) {
            if (answer == null || safe(answer.answer()).isBlank()) {
                continue;
            }
            lines.add("- " + label(answer) + "\n  Question: " + safe(answer.question())
                    + "\n  Answer: " + safe(answer.answer())
                    + evidenceLine(answer));
        }

        String note = safe(additionalInfo);
        if (!note.isBlank()) {
            lines.add("- Additional note\n  Answer: " + note);
        }
        return String.join("\n", lines).trim();
    }

    public boolean hasClarification() {
        return !clarificationText().isBlank();
    }

    private static String label(ClarificationAnswer answer) {
        String type = safe(answer.type());
        String category = safe(answer.category());
        if (!category.isBlank()) {
            return type.isBlank() ? category : type + " - " + category;
        }
        return type.isBlank() ? "clarification" : type;
    }

    private static String evidenceLine(ClarificationAnswer answer) {
        String evidence = safe(answer.evidence());
        return evidence.isBlank() ? "" : "\n  Evidence: " + evidence;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record ClarificationAnswer(
            String type,
            String category,
            String question,
            String answer,
            String evidence) {
    }
}
