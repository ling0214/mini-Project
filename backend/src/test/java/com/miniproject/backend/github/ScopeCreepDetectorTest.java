package com.miniproject.backend.github;

import com.miniproject.backend.skills.ImpactAnalysisResult.AffectedModule;
import com.miniproject.backend.skills.ImpactAnalysisResult.ScopeCreepFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeCreepDetectorTest {

    private final ScopeCreepDetector detector = new ScopeCreepDetector();

    @Test
    void exactPathMatchProducesNoFinding() {
        List<GitHubPrReader.PrFile> changed = List.of(
                new GitHubPrReader.PrFile("src/CheckoutController.php", "modified", "patch"));
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "src/CheckoutController.php:42", "matched", "src/CheckoutController.php:42"));

        assertThat(detector.detect(changed, declared)).isEmpty();
    }

    @Test
    void undeclaredChangeIsFlaggedWhenNoDeclaredModuleMatches() {
        List<GitHubPrReader.PrFile> changed = List.of(
                new GitHubPrReader.PrFile("src/CheckoutController.php", "modified", "patch"),
                new GitHubPrReader.PrFile("src/UnrelatedHelper.php", "modified", "patch"));
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "src/CheckoutController.php:42", "matched", "src/CheckoutController.php:42"));

        List<ScopeCreepFinding> findings = detector.detect(changed, declared);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).path()).isEqualTo("src/UnrelatedHelper.php");
        assertThat(findings.get(0).kind()).isEqualTo("undeclared_change");
    }

    @Test
    void declaredButUntouchedModuleIsFlagged() {
        List<GitHubPrReader.PrFile> changed = List.of();
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "src/CheckoutController.php:42", "matched", "src/CheckoutController.php:42"));

        List<ScopeCreepFinding> findings = detector.detect(changed, declared);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).path()).isEqualTo("src/CheckoutController.php:42");
        assertThat(findings.get(0).kind()).isEqualTo("declared_untouched");
    }

    @Test
    void backslashPathsNormalizeToMatchForwardSlashFilenames() {
        List<GitHubPrReader.PrFile> changed = List.of(
                new GitHubPrReader.PrFile("src/CheckoutController.php", "modified", "patch"));
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "src\\CheckoutController.php:42", "matched", "src\\CheckoutController.php:42"));

        assertThat(detector.detect(changed, declared)).isEmpty();
    }

    @Test
    void suffixMatchBridgesDifferentlyRootedPaths() {
        // Mirrors the pruserveplus-ipad case: the codebase graph was indexed
        // against a subfolder, so its "file" is relative to that subfolder,
        // while GitHub's filename is relative to the full repo root.
        List<GitHubPrReader.PrFile> changed = List.of(
                new GitHubPrReader.PrFile("pruserveplus-ipad/LeadMgmt/CheckoutController.swift", "modified", "patch"));
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "LeadMgmt/CheckoutController.swift:12", "matched", "LeadMgmt/CheckoutController.swift:12"));

        assertThat(detector.detect(changed, declared)).isEmpty();
    }

    @Test
    void unrelatedFilenamesDoNotFalsePositiveOnPartialSegmentOverlap() {
        // "MyCheckoutController.php" must NOT be treated as related to
        // "CheckoutController.php" just because one string contains the other.
        List<GitHubPrReader.PrFile> changed = List.of(
                new GitHubPrReader.PrFile("src/MyCheckoutController.php", "modified", "patch"));
        List<AffectedModule> declared = List.of(
                new AffectedModule("CheckoutController", "src/CheckoutController.php:42", "matched", "src/CheckoutController.php:42"));

        List<ScopeCreepFinding> findings = detector.detect(changed, declared);

        assertThat(findings).extracting(ScopeCreepFinding::kind)
                .containsExactlyInAnyOrder("undeclared_change", "declared_untouched");
    }

    @Test
    void emptyInputsProduceNoFindings() {
        assertThat(detector.detect(List.of(), List.of())).isEmpty();
        assertThat(detector.detect(null, null)).isEmpty();
    }
}
