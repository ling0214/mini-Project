package com.miniproject.backend.skills;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnMissingBean(AiAnalysisClient.class)
public class NoopAiAnalysisClient implements AiAnalysisClient {

    @Override
    public Optional<String> analyze(String systemPrompt, String userPrompt) {
        return Optional.empty();
    }
}
