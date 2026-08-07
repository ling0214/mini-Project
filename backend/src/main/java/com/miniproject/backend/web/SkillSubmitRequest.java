package com.miniproject.backend.web;

public record SkillSubmitRequest(
    String sourceArtifactId,
    String version,
    String notes,
    String submittedBy
) {}
