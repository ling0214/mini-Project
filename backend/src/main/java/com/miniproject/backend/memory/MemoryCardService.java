package com.miniproject.backend.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.skills.TextTermExtractor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and queries memory cards (Section: MemoryCardEntity). Best-effort by
 * design: recordReviewed() never throws past a log-worthy failure, since a
 * memory-card write is a side effect of review, not something that should be
 * able to block the review flow itself if a result shape is unexpected.
 */
@Service
public class MemoryCardService {

    private static final Set<String> SUPPORTED_SKILLS = Set.of("requirement-analysis", "impact-analysis");
    private static final int MAX_RESULTS = 3;

    private final MemoryCardRepository repository;
    private final ObjectMapper objectMapper;

    public MemoryCardService(MemoryCardRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void recordReviewed(String taskId, String skill, String resultJson) {
        if (!SUPPORTED_SKILLS.contains(skill)) {
            return;
        }
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(resultJson, Map.class);
        } catch (JsonProcessingException e) {
            return;
        }

        String summary = "requirement-analysis".equals(skill) ? summarizeRequirement(result) : summarizeImpact(result);
        if (summary.isBlank()) {
            return;
        }
        String terms = String.join(" ", TextTermExtractor.extractTerms(summary));
        if (terms.isBlank()) {
            return;
        }
        repository.save(new MemoryCardEntity(taskId, skill, summary, terms, Instant.now()));
    }

    public List<SimilarPastChange> findSimilar(String skill, String changeRequestText, String excludeTaskId) {
        Set<String> queryTerms = TextTermExtractor.extractTerms(changeRequestText);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        return repository.findBySkill(skill).stream()
                .filter(card -> !card.getTaskId().equals(excludeTaskId))
                .map(card -> scored(card, queryTerms))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(MAX_RESULTS)
                .map(Scored::toSimilarPastChange)
                .toList();
    }

    private Scored scored(MemoryCardEntity card, Set<String> queryTerms) {
        Set<String> cardTerms = Set.of(card.getSearchTerms().split(" "));
        int overlap = 0;
        for (String term : queryTerms) {
            if (cardTerms.contains(term)) {
                overlap++;
            }
        }
        return new Scored(card, overlap);
    }

    private record Scored(MemoryCardEntity card, int score) {
        SimilarPastChange toSimilarPastChange() {
            return new SimilarPastChange(card.getTaskId(), card.getSummaryMarkdown(), score, card.getCreatedAt().toString());
        }
    }

    private String summarizeRequirement(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, asString(result.get("business_value")));
        appendJoined(sb, result.get("business_rules"));
        appendJoined(sb, result.get("potential_affected_areas"));
        return sb.toString().trim();
    }

    private String summarizeImpact(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();
        String riskLevel = asString(result.get("risk_level"));
        if (!riskLevel.isBlank()) {
            sb.append("Risk: ").append(riskLevel).append(". ");
        }
        Object modules = result.get("affected_modules");
        if (modules instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object name = map.get("name");
                    Object reason = map.get("reason");
                    if (name != null) {
                        sb.append(name);
                        if (reason != null) {
                            sb.append(" (").append(reason).append(")");
                        }
                        sb.append("; ");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (!value.isBlank()) {
            sb.append(value).append(' ');
        }
    }

    private static void appendJoined(StringBuilder sb, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                sb.append(item).append(' ');
            }
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
