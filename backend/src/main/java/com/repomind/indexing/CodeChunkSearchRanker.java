package com.repomind.indexing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Combines vector relevance with generic path, symbol, and source-term matches. */
final class CodeChunkSearchRanker {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_$]+");
    private static final Set<String> STOP_WORDS = Set.of("which", "what", "where", "when", "who", "how",
            "does", "do", "is", "are", "the", "a", "an", "and", "or", "to", "for", "of", "in",
            "on", "with", "that", "this", "it", "be", "by", "from", "show", "find", "tell", "me",
            "code", "file", "files", "class");

    List<RankedChunk> rank(String question, List<CodeChunkEntity> chunks, Map<Long, Double> vectorScores,
                           int limit) {
        List<String> terms = terms(question);
        return chunks.stream().map(chunk -> {
                    double semantic = clamp(vectorScores.getOrDefault(chunk.getId(), 0d));
                    Set<String> pathTokens = tokens(chunk.getFilePath());
                    Set<String> contentTokens = tokens(chunk.getContent());
                    long pathMatches = terms.stream().filter(term -> matches(pathTokens, term)).count();
                    long sourceMatches = terms.stream().filter(term -> matches(contentTokens, term)).count();
                    double pathCoverage = coverage(pathMatches, terms.size());
                    double sourceCoverage = coverage(sourceMatches, terms.size());
                    boolean sourceFile = chunk.getFilePath().toLowerCase(Locale.ROOT)
                            .matches(".*\\.(java|kt|kts|js|jsx|mjs|cjs|ts|tsx|py|go|rs|cs|cpp|c|h|hpp|rb|php|swift|scala|sql)$");
                    double score = 0.45 * semantic + 0.20 * sourceCoverage + 0.35 * pathCoverage
                            + (sourceFile ? 0.12 : 0d);
                    return new RankedChunk(chunk, clamp(score));
                })
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed()
                        .thenComparing(item -> item.chunk().getFilePath())
                        .thenComparingInt(item -> item.chunk().getLineStart()))
                .limit(limit)
                .toList();
    }

    static String regexFor(String question) {
        List<String> terms = terms(question);
        if (terms.isEmpty()) return "(?!)";
        return "(" + String.join("|", terms) + ")";
    }

    private static List<String> terms(String value) {
        List<String> terms = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String term : tokens(value)) {
            if (term.length() >= 3 && !STOP_WORDS.contains(term) && seen.add(term)) terms.add(term);
            if (terms.size() == 12) break;
        }
        return terms;
    }

    private static Set<String> tokens(String value) {
        Matcher matcher = TOKEN.matcher(normalize(value));
        Set<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) tokens.add(matcher.group());
        return tokens;
    }

    private static String normalize(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    private static boolean matches(Set<String> tokens, String term) {
        return tokens.stream().anyMatch(token -> token.equals(term) || token.startsWith(term));
    }

    private static double coverage(long matches, int size) {
        return size == 0 ? 0 : (double) matches / size;
    }

    private static double clamp(double score) {
        return Math.max(0d, Math.min(1d, score));
    }

    record RankedChunk(CodeChunkEntity chunk, double score) { }
}
