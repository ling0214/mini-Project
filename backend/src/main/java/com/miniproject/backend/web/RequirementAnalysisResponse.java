package com.miniproject.backend.web;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.skills.RequirementAnalysisResult;

public record RequirementAnalysisResponse(Artifact<RequirementAnalysisResult> artifact, AnalysisStatus status) {

    public static RequirementAnalysisResponse of(Artifact<RequirementAnalysisResult> artifact) {
        return new RequirementAnalysisResponse(artifact, AnalysisStatus.from(artifact.result()));
    }
}
