package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * First cut at RequirementAnalysisSkill's synthesis step: keyword/pattern
 * heuristics only, no LLM call — same "rule-based first" position as
 * RuleBasedImpactAnalysisSynthesizer. Known limitation, stated up front
 * rather than discovered by a reviewer: this can flag *lexical* signals of
 * ambiguity (hedge words, missing actor, no stated outcome) but cannot judge
 * whether a requirement is actually clear. A future LLM-backed
 * RequirementAnalysisSynthesizer is expected to replace this for real
 * semantic judgment — see docs/architecture.md.
 */
@Component
public class RuleBasedRequirementAnalysisSynthesizer implements RequirementAnalysisSynthesizer {

    private static final List<String> HEDGE_WORDS = List.of(
            "tbd", "maybe", "possibly", "somehow", "eventually", "at some point",
            "if possible", "e.g.", "etc", "and so on", "later", "not sure", "might",
            "should probably", "i think", "roughly", "approximately");

    private static final List<String> RULE_MARKERS = List.of(
            "must", "should", "shall", "cannot", "can't", "only if", "unless", "always", "never");

    private static final List<String> ACTOR_WORDS = List.of(
            "user", "customer", "analyst", "admin", "administrator", "agent", "tester",
            "system", "reviewer", "manager", "client", "applicant", "policyholder",
            "donor", "victim", "superadmin", "center admin", "collection center admin",
            "stakeholder", "requester", "reporter");

    private static final int MIN_WORDS_FOR_CONCRETE_SCOPE = 8;

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\R+");

    @Override
    public RequirementAnalysisResult synthesize(String description, List<String> sentences, List<String> candidateAreas) {
        String lower = description.toLowerCase(Locale.ROOT);

        List<String> missingInformation = new ArrayList<>();
        List<RequirementAnalysisResult.Ambiguity> ambiguities = new ArrayList<>();
        List<String> businessRules = new ArrayList<>();
        List<RequirementAnalysisResult.AnalystConcern> analystConcerns = analystConcerns(lower, description);

        int wordCount = description.trim().isEmpty() ? 0 : description.trim().split("\\s+").length;
        if (wordCount < MIN_WORDS_FOR_CONCRETE_SCOPE) {
            missingInformation.add("Description is only " + wordCount + " word(s) — too short to extract a concrete scope; ask the requester for more detail.");
        }

        boolean hasActor = ACTOR_WORDS.stream().anyMatch(lower::contains);
        if (!hasActor) {
            missingInformation.add("No actor/role (e.g. \"user\", \"customer\", \"analyst\") found in the description — unclear who this change is for.");
        }

        if (candidateAreas.isEmpty()) {
            missingInformation.add("No specific system/module/feature name found in the description — unclear what part of the system is affected.");
        }

        for (String hedge : HEDGE_WORDS) {
            if (lower.contains(hedge)) {
                ambiguities.add(new RequirementAnalysisResult.Ambiguity(
                        "Hedge word \"" + hedge + "\" found — the requirement is not stated as a firm decision.", description));
            }
        }

        for (String sentence : sentences) {
            String sentenceLower = sentence.toLowerCase(Locale.ROOT);
            if (RULE_MARKERS.stream().anyMatch(sentenceLower::contains)) {
                businessRules.add(sentence.trim());
            }
        }

        List<String> assumptions = businessRules.isEmpty()
                ? List.of("No explicit business rule (must/should/shall/unless/...) was stated; assume none beyond what's written.")
                : List.of();
        String businessValue = businessValue(lower);
        RequirementAnalysisResult.ScopeBoundary scopeBoundary =
                scopeBoundary(candidateAreas, businessRules, assumptions, lower);
        List<RequirementAnalysisResult.ProjectRisk> projectRisks =
                projectRisks(analystConcerns, ambiguities, missingInformation);

        String confidence = (!ambiguities.isEmpty() || !missingInformation.isEmpty()) ? "low" : "medium";

        List<Evidence> evidence = new ArrayList<>();
        for (String rule : businessRules) {
            evidence.add(new Evidence(rule, "requirement text"));
        }

        return new RequirementAnalysisResult(
                businessRules,
                businessValue,
                scopeBoundary,
                ambiguities,
                missingInformation,
                assumptions,
                analystConcerns,
                projectRisks,
                candidateAreas,
                confidence,
                evidence,
                List.of());
    }

    private String businessValue(String lower) {
        if (containsAny(lower, "donor", "victim", "aid", "request", "flood")) {
            return "Helps aid-related users complete the operational workflow faster and with clearer access to relevant request data.";
        }
        if (containsAny(lower, "notify", "notification", "alert", "email")) {
            return "Improves operational response time by making important status changes visible to the responsible stakeholders.";
        }
        if (containsAny(lower, "report", "export", "dashboard", "csv", "excel")) {
            return "Improves reporting accuracy and reduces manual analyst effort when preparing stakeholder updates.";
        }
        if (containsAny(lower, "payment", "checkout", "promo", "card")) {
            return "Reduces checkout friction and supports successful customer transactions.";
        }
        return "Clarifies the requested business change so the team can decide scope, risk, testing effort, and delivery priority.";
    }

    private RequirementAnalysisResult.ScopeBoundary scopeBoundary(
            List<String> candidateAreas, List<String> businessRules, List<String> assumptions, String lower) {
        List<String> inScope = new ArrayList<>();
        if (!businessRules.isEmpty()) {
            inScope.addAll(businessRules);
        } else if (!candidateAreas.isEmpty()) {
            inScope.add("Assess and update the mentioned area(s): " + String.join(", ", candidateAreas));
        } else {
            inScope.add("Clarify the requested behavior before confirming implementation scope.");
        }

        List<String> outOfScope = new ArrayList<>();
        outOfScope.add("Unmentioned screens, workflows, integrations, and historical data migration are out of scope until confirmed.");
        if (containsAny(lower, "filter", "search", "sort", "list")) {
            outOfScope.add("Advanced analytics, new report formats, and bulk data export are out of scope unless separately requested.");
        }

        List<String> dependencies = new ArrayList<>();
        if (containsAny(lower, "admin", "superadmin", "donor", "victim", "approved", "access", "role")) {
            dependencies.add("Confirmed role permission matrix from stakeholder or product owner.");
        }
        if (containsAny(lower, "filter", "search", "sort", "list", "records", "report")) {
            dependencies.add("Expected data volume, pagination, and performance target from developer or code owner.");
        }
        if (!assumptions.isEmpty()) {
            dependencies.add("Stakeholder confirmation for assumptions before development handoff.");
        }
        dependencies.add("QA/UAT owner confirmation for acceptance and regression coverage.");

        return new RequirementAnalysisResult.ScopeBoundary(inScope, outOfScope, dependencies);
    }

    private List<RequirementAnalysisResult.ProjectRisk> projectRisks(
            List<RequirementAnalysisResult.AnalystConcern> analystConcerns,
            List<RequirementAnalysisResult.Ambiguity> ambiguities,
            List<String> missingInformation) {
        List<RequirementAnalysisResult.ProjectRisk> risks = new ArrayList<>();
        for (RequirementAnalysisResult.AnalystConcern concern : analystConcerns) {
            risks.add(riskFromConcern(concern));
        }
        if (!missingInformation.isEmpty()) {
            risks.add(new RequirementAnalysisResult.ProjectRisk(
                    "P2",
                    "high",
                    "scope",
                    "Missing information can delay agreement on project scope and acceptance criteria.",
                    "Resolve missing items through clarification before review approval.",
                    "Software Analyst"));
        }
        if (!ambiguities.isEmpty()) {
            risks.add(new RequirementAnalysisResult.ProjectRisk(
                    "P2",
                    "high",
                    "change_control",
                    "Ambiguous wording may cause stakeholder disagreement or rework after development starts.",
                    "Record a confirmed decision before impact analysis and testing scope are finalized.",
                    "Software Analyst"));
        }
        if (risks.isEmpty()) {
            risks.add(new RequirementAnalysisResult.ProjectRisk(
                    "P3",
                    "low",
                    "delivery",
                    "No critical project risk detected from the requirement text.",
                    "Continue through impact analysis and QA scope review.",
                    "Software Analyst"));
        }
        return risks;
    }

    private RequirementAnalysisResult.ProjectRisk riskFromConcern(RequirementAnalysisResult.AnalystConcern concern) {
        return switch (concern.category()) {
            case "privacy", "security", "compliance" -> new RequirementAnalysisResult.ProjectRisk(
                    "P1",
                    "critical",
                    concern.category(),
                    concern.note(),
                    concern.question(),
                    "Software Analyst / Product Owner");
            case "role_access", "availability" -> new RequirementAnalysisResult.ProjectRisk(
                    "P2",
                    "high",
                    concern.category(),
                    concern.note(),
                    concern.question(),
                    "Software Analyst / Developer");
            case "performance", "data_quality", "audit" -> new RequirementAnalysisResult.ProjectRisk(
                    "P3",
                    "medium",
                    concern.category(),
                    concern.note(),
                    concern.question(),
                    "Developer / QA");
            default -> new RequirementAnalysisResult.ProjectRisk(
                    "P3",
                    concern.severity(),
                    concern.category(),
                    concern.note(),
                    concern.question(),
                    "QA / Software Analyst");
        };
    }

    private List<RequirementAnalysisResult.AnalystConcern> analystConcerns(String lower, String description) {
        List<RequirementAnalysisResult.AnalystConcern> concerns = new ArrayList<>();
        if (containsAny(lower, "victim", "donor", "address", "phone", "email", "location", "city", "nric", "identity card")) {
            concerns.add(new RequirementAnalysisResult.AnalystConcern(
                    "privacy",
                    "medium",
                    "Requirement may expose personal, location, donor, or aid-request data.",
                    description,
                    "Confirm which fields are visible to each role and whether personal data needs masking or consent."));
        }
        if (containsAny(lower, "admin", "superadmin", "donor", "victim", "approved", "access", "role")) {
            concerns.add(new RequirementAnalysisResult.AnalystConcern(
                    "role_access",
                    "medium",
                    "Requirement appears to depend on who is allowed to view or change records.",
                    description,
                    "Confirm the role permission matrix and whether unapproved records must be hidden."));
        }
        if (containsAny(lower, "filter", "search", "sort", "list", "records", "report")) {
            concerns.add(new RequirementAnalysisResult.AnalystConcern(
                    "performance",
                    "low",
                    "Listing or filtering changes may affect response time when data grows.",
                    description,
                    "Confirm expected record volume, pagination behavior, and whether database indexes are needed."));
        }
        if (containsAny(lower, "must", "should", "given", "when", "then", "filter", "update", "change")) {
            concerns.add(new RequirementAnalysisResult.AnalystConcern(
                    "testing",
                    "low",
                    "Change needs positive, negative, and regression test coverage before handoff.",
                    description,
                    "Confirm the main acceptance path, denied-access cases, and affected regression areas."));
        }
        return concerns;
    }

    private boolean containsAny(String lower, String... needles) {
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String s : SENTENCE_SPLIT.split(text.trim())) {
            if (!s.isBlank()) {
                sentences.add(s.trim());
            }
        }
        return sentences;
    }
}
