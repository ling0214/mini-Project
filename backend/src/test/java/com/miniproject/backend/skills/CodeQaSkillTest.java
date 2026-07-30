package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeQaSkillTest {

    @Test
    void resolvesIdentifierTokensAndSkipsStopwords() {
        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.getEndpointInfo("charge_card")).thenReturn(Map.of(
                "found", true,
                "name", "charge_card",
                "file", "payments.py",
                "line", 1,
                "calls", List.of("_validate_token", "_submit_to_gateway"),
                "called_by", List.of("checkout_endpoint")));
        // "what" / "does" / "depend" are stopwords and must not be looked up.
        when(graphClient.getEndpointInfo("what")).thenThrow(new AssertionError("stopword should not be queried"));
        when(graphClient.searchIssues(anyString())).thenReturn(Map.of("query", "x", "matches", List.of(), "count", 0));

        AnswerSynthesizer synthesizer = mock(AnswerSynthesizer.class);
        when(synthesizer.synthesize(anyString(), any(), any())).thenReturn(
                new CodeQaResult("stub answer", List.of(), List.of()));

        CodeQaSkill skill = new CodeQaSkill(graphClient, synthesizer);

        CodeQaResult result = skill.run("what does charge_card depend on?");

        assertThat(result.answer()).isEqualTo("stub answer");
    }

    @Test
    void unresolvedNameIsExcludedFromFactsPassedToSynthesizer() {
        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.getEndpointInfo(anyString())).thenReturn(Map.of("found", false, "name", "nope"));
        when(graphClient.searchIssues(anyString())).thenReturn(Map.of("query", "x", "matches", List.of(), "count", 0));
        when(graphClient.searchProjectContext(anyString(), anyString(), any(Integer.class)))
                .thenReturn(Map.of("project", "MyBanjirCare", "matches", List.of(), "count", 0));

        AnswerSynthesizer synthesizer = mock(AnswerSynthesizer.class);
        when(synthesizer.synthesize(anyString(), any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> resolved = invocation.getArgument(1);
            assertThat(resolved).isEmpty();
            return new CodeQaResult("no matches", List.of(), List.of("nothing resolved"));
        });

        CodeQaSkill skill = new CodeQaSkill(graphClient, synthesizer);

        CodeQaResult result = skill.run("what does totally_unknown_function do?");

        assertThat(result.ungrounded()).containsExactly("nothing resolved");
    }

    @Test
    void searchesProjectContextForNaturalLanguageRepoQuestions() {
        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.getEndpointInfo(anyString())).thenReturn(Map.of("found", false, "name", "nope"));
        when(graphClient.searchIssues(anyString())).thenReturn(Map.of("query", "x", "matches", List.of(), "count", 0));
        when(graphClient.searchProjectContext(eq("MyBanjirCare"), anyString(), eq(8)))
                .thenReturn(Map.of(
                        "project", "MyBanjirCare",
                        "matches", List.of(Map.of(
                                "name", "DonationController",
                                "file", "app/Http/Controllers/DonationController.php",
                                "line", 42,
                                "reason", "codebase-memory matched class DonationController as relevant to this ticket")),
                        "count", 1));

        CodeQaSkill skill = new CodeQaSkill(graphClient, new RuleBasedAnswerSynthesizer());

        CodeQaResult result = skill.run("What should I check before changing donation status?");

        assertThat(result.answer()).contains("DonationController");
        assertThat(result.evidence()).extracting("source").contains("app/Http/Controllers/DonationController.php:42");
        verify(graphClient).searchProjectContext(eq("MyBanjirCare"), anyString(), eq(8));
    }

    @Test
    void ruleBasedSynthesizerCitesFileAndLineForEachResolvedFunction() {
        RuleBasedAnswerSynthesizer synthesizer = new RuleBasedAnswerSynthesizer();

        CodeQaResult result = synthesizer.synthesize(
                "what does charge_card depend on?",
                List.of(Map.of(
                        "found", true,
                        "name", "charge_card",
                        "file", "payments.py",
                        "line", 1,
                        "calls", List.of("_validate_token"),
                        "called_by", List.of("checkout_endpoint"))),
                Map.of("query", "x", "matches", List.of(
                        Map.of("id", 108, "title", "Payment gateway timeout not retried", "state", "open")), "count", 1));

        assertThat(result.answer()).contains("payments.py:1");
        assertThat(result.answer()).contains("_validate_token");
        assertThat(result.answer()).contains("#108");
        assertThat(result.evidence()).extracting("source").contains("payments.py:1", "issue #108");
        assertThat(result.ungrounded()).isEmpty();
    }

    @Test
    void ruleBasedSynthesizerFlagsMissingEvidenceWhenNothingResolves() {
        RuleBasedAnswerSynthesizer synthesizer = new RuleBasedAnswerSynthesizer();

        CodeQaResult result = synthesizer.synthesize(
                "what does totally_unknown_function do?",
                List.of(),
                Map.of("query", "x", "matches", List.of(), "count", 0));

        assertThat(result.ungrounded()).isNotEmpty();
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    void ruleBasedSynthesizerUsesProjectContextMatchesAsEvidence() {
        RuleBasedAnswerSynthesizer synthesizer = new RuleBasedAnswerSynthesizer();

        CodeQaResult result = synthesizer.synthesize(
                "What handles donation status?",
                List.of(),
                Map.of(
                        "matches", List.of(),
                        "project_context", Map.of(
                                "matches", List.of(Map.of(
                                        "name", "DonationController",
                                        "file", "app/Http/Controllers/DonationController.php",
                                        "line", 42,
                                        "reason", "codebase-memory matched class DonationController as relevant to this ticket")))));

        assertThat(result.answer()).contains("Relevant project context").contains("DonationController");
        assertThat(result.evidence()).extracting("source").contains("app/Http/Controllers/DonationController.php:42");
    }
}
