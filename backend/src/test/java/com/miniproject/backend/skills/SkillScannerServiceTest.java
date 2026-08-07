package com.miniproject.backend.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SkillScannerServiceTest {

    private SkillScannerService scannerService;

    @BeforeEach
    public void setUp() {
        scannerService = new SkillScannerService();
    }

    @Test
    public void testScanSkill_WithNoIssues() {
        String cleanCode = "public class MySkill { public void run() { System.out.println(\"Hello\"); } }";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", cleanCode);

        assertEquals("my-skill", result.skillName);
        assertEquals(SkillScannerService.ScanStatus.PASS, result.status);
        assertTrue(result.findings.isEmpty());
    }

    @Test
    public void testScanSkill_DetectsHardcodedSecret() {
        String codeWithSecret = "String apiKey = \"sk_live_12345abcde\";";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", codeWithSecret);

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertFalse(result.findings.isEmpty());
        assertTrue(result.findings.stream().anyMatch(f -> f.title.contains("secret")));
    }

    @Test
    public void testScanSkill_DetectsRuntimeFetching() {
        String codeWithRuntimeFetch = "code contains Runtime.getRuntime().exec() which is bad";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", codeWithRuntimeFetch);

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertTrue(result.findings.stream().anyMatch(f -> f.title.contains("Runtime")));
    }

    @Test
    public void testScanSkill_DetectsEval() {
        String codeWithEval = "String code = \"malicious\"; eval(code);";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", codeWithEval);

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertTrue(result.findings.stream().anyMatch(f -> f.title.contains("Runtime")));
    }

    @Test
    public void testScanSkill_WithNullCode_FlagsAsUnresolvedNotClean() {
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", null);

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertFalse(result.findings.isEmpty());
    }

    @Test
    public void testScanSkill_WithEmptyCode_FlagsAsUnresolvedNotClean() {
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", "");

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertFalse(result.findings.isEmpty());
    }

    @Test
    public void testScanFinding_HasCorrectSeverity() {
        String codeWithSecret = "password = \"secret123\";";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", codeWithSecret);

        assertFalse(result.findings.isEmpty());
        assertTrue(result.findings.stream().anyMatch(f -> f.severity == SkillScannerService.ScanSeverity.HIGH));
    }

    @Test
    public void testScanSkill_MultipleFindings() {
        String codeWithMultipleIssues = "String token = \"secret\"; Object result = Runtime.getRuntime().exec();";
        SkillScannerService.SkillScanResult result = scannerService.scanSkill("my-skill", codeWithMultipleIssues);

        assertEquals(SkillScannerService.ScanStatus.REVIEW_NEEDED, result.status);
        assertTrue(result.findings.size() >= 2);
    }
}
