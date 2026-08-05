package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/**
 * Generates config/profile scaffolding for a new Hermes deployment — never
 * written to Hermes's own files directly (see decision log in the mini-Project
 * <-> Hermes implementation plan: generate-only, human applies it). The
 * analyst copies generatedYaml in by hand after working through checklist.
 */
public record HermesSetupWizardResult(
        HermesSetupWizardAnswers answers,
        String generatedYaml,
        List<String> checklist,
        List<String> notes,
        List<Evidence> evidence) {
}
