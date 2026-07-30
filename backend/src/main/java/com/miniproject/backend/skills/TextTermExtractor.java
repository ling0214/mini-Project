package com.miniproject.backend.skills;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared keyword extraction: strip stopwords, split camelCase, keep words
 * >= 3 chars. Originally lived only inside ProjectContextMatcher (ticket ->
 * code file matching); pulled out so MemoryCardService (ticket -> similar
 * past ticket matching, Section: memory/Similar Past Changes) can reuse the
 * exact same heuristic instead of a second copy drifting out of sync.
 */
public final class TextTermExtractor {

    private static final Pattern WORD = Pattern.compile("[a-zA-Z][a-zA-Z0-9_\\-]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "ticket", "key", "title", "priority", "high", "medium", "low", "critical", "reporter",
            "mbc", "fyp", "supervisor", "owner", "product",
            "description", "acceptance", "criteria", "comments", "notes", "given", "when",
            "then", "should", "could", "would", "must", "able", "allow", "allows", "before",
            "after", "only", "shows", "matching", "records", "page", "update", "change",
            "add", "remove", "filter", "filters", "select", "selects", "help", "need",
            "confirm", "confirmed", "whether", "through", "reload", "ajax", "system", "user",
            "the", "and", "are", "this", "that", "for", "shown", "available");

    private TextTermExtractor() {
    }

    public static Set<String> extractTerms(String text) {
        String normalized = splitCamelCase(text == null ? "" : text).toLowerCase(Locale.ROOT);
        Matcher matcher = WORD.matcher(normalized);
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = matcher.group().replace("-", " ").trim();
            if (!STOPWORDS.contains(term) && term.length() >= 3) {
                terms.add(term);
            }
        }
        return terms;
    }

    public static String splitCamelCase(String text) {
        return text
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
    }
}
