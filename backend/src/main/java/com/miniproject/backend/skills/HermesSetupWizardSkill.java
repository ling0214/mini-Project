package com.miniproject.backend.skills;

import org.springframework.stereotype.Component;

/**
 * Weakness #1 remediation ("Hermes setup is too heavy") — see the
 * mini-Project <-> Hermes implementation plan. Generates config/profile
 * scaffolding only; never writes into Hermes's own files (decision locked in
 * with the user: generate-only, human applies it by hand).
 */
@Component
public class HermesSetupWizardSkill {

    private final HermesSetupWizardSynthesizer synthesizer;

    public HermesSetupWizardSkill(HermesSetupWizardSynthesizer synthesizer) {
        this.synthesizer = synthesizer;
    }

    public HermesSetupWizardResult run(HermesSetupWizardAnswers answers) {
        if (answers == null || answers.repoPath() == null || answers.repoPath().isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        return synthesizer.synthesize(answers);
    }
}
