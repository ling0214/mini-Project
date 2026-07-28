package com.miniproject.backend.skills;

import java.util.Optional;

public interface AiAnalysisClient {

    Optional<String> analyze(String systemPrompt, String userPrompt);
}
