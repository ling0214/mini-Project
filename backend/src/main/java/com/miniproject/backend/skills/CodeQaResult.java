package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Output shape from skills/code-qa.md. */
public record CodeQaResult(String answer, List<Evidence> evidence, List<String> ungrounded) {
}
