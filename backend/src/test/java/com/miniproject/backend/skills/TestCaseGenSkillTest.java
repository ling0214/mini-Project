package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCaseGenSkillTest {

    @Test
    void fallsBackToCodebaseMemoryProjectContextWhenEndpointInfoDoesNotResolve() {
        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.getEndpointInfo("approvedAidRequests"))
                .thenReturn(Map.of("found", false, "name", "approvedAidRequests"));
        when(graphClient.searchProjectContext("MyBanjirCare", "approvedAidRequests", 1))
                .thenReturn(Map.of(
                        "matches", List.of(Map.of(
                                "name", "approvedAidRequests",
                                "file", "app/Models/CollectionCenter.php",
                                "line", 62))));
        when(graphClient.getTestCoverage("approvedAidRequests"))
                .thenReturn(Map.of("found", true, "covered_by", List.of()));
        when(graphClient.searchIssues("approvedAidRequests"))
                .thenReturn(Map.of("matches", List.of()));

        TestCaseGenSkill skill = new TestCaseGenSkill(
                graphClient,
                new RuleBasedTestCaseGenSynthesizer(),
                "MyBanjirCare");

        TestCaseGenResult result = skill.run("approvedAidRequests");

        assertThat(result.cases()).hasSize(2);
        assertThat(result.cases()).extracting(TestCaseGenResult.TestCase::evidence)
                .contains("app/Models/CollectionCenter.php:62");
        assertThat(result.missingEvidence()).noneMatch(item -> item.contains("not found"));
    }
}
