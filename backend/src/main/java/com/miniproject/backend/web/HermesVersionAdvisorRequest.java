package com.miniproject.backend.web;

import java.util.List;

public record HermesVersionAdvisorRequest(
        String profile, String repoPath, String remoteRef, String localRef, List<String> watchedPaths) {
}
