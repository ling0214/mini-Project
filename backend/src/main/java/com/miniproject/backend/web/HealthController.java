package com.miniproject.backend.web;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Plain liveness check for the frontend's connectivity indicator. Kept
 * separate from the skill endpoints so pinging it never runs a skill or
 * writes an artifact — checkBackend() in the frontend used to POST a fake
 * "__ping__" question to /api/skills/code-qa, which persisted a junk row
 * into analysis_artifacts on every page load once Phase 3.5 added
 * persistence (Section 5.6).
 */
@RestController
public class HealthController {

    @CrossOrigin(origins = "*")
    @GetMapping("/api/health")
    public Map<String, Boolean> health() {
        return Map.of("up", true);
    }
}
