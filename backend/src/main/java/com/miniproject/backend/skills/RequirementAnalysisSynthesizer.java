package com.miniproject.backend.skills;

import java.util.List;

/**
 * Same seam as ImpactAnalysisSynthesizer/AnswerSynthesizer: this interface is
 * the swap point if requirement-ambiguity detection ever moves from rules to
 * an LLM call. RequirementAnalysisSkill only ever depends on this interface.
 */
public interface RequirementAnalysisSynthesizer {

    RequirementAnalysisResult synthesize(String description, List<String> sentences, List<String> candidateAreas);
}
