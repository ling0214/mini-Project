package com.miniproject.backend.skills;

/**
 * Same seam as RequirementAnalysisSynthesizer/ImpactAnalysisSynthesizer:
 * HermesSetupWizardSkill only ever depends on this interface, so the rule-based
 * template-fill implementation can be swapped for the LLM one via
 * analysis.hermes-setup.provider without touching the skill itself.
 */
public interface HermesSetupWizardSynthesizer {

    HermesSetupWizardResult synthesize(HermesSetupWizardAnswers answers);
}
