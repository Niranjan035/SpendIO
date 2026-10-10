package com.spendio.imports.matcher;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Fuzzy account matching (ARCHITECTURE.md D.12 step 4): exact name wins, then
 * normalised-equality, then token-overlap (Jaccard) with a 0.75 confidence floor.
 */
public final class AccountMatcher {

    public static final double MIN_CONFIDENCE = 0.75;

    public static final BigDecimal MIN_CONFIDENCE_BD = BigDecimal.valueOf(MIN_CONFIDENCE);

    private AccountMatcher() {
    }

    public record Candidate(Long id, String name) {
    }

    public record Match(Long candidateId, String matchedBy, BigDecimal confidence) {
    }

    public static Optional<Match> match(String text, List<Candidate> candidates) {
        if (text == null || text.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String target = text.trim();
        String targetNorm = DescriptionNormalizer.normalize(target);
        Set<String> targetTokens = tokens(targetNorm);

        Candidate best = null;
        String bestBy = null;
        double bestScore = -1.0;

        for (Candidate candidate : candidates) {
            String nameNorm = DescriptionNormalizer.normalize(candidate.name());
            if (candidate.name().trim().equalsIgnoreCase(target)) {
                return Optional.of(new Match(candidate.id(), "EXACT", BigDecimal.ONE));
            }
            double score;
            String by;
            if (nameNorm.equals(targetNorm)) {
                score = 0.98;
                by = "NORMALIZED";
            } else {
                score = jaccard(targetTokens, tokens(nameNorm));
                by = "FUZZY";
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                bestBy = by;
            }
        }

        if (best == null || bestScore < MIN_CONFIDENCE) {
            return Optional.empty();
        }
        return Optional.of(new Match(best.id(), bestBy, BigDecimal.valueOf(scoreRounded(bestScore))));
    }

    static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        int intersection = (int) left.stream().filter(right::contains).count();
        return (double) intersection / union.size();
    }

    static Set<String> tokens(String normalised) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalised.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double scoreRounded(double score) {
        return Math.round(score * 1000.0) / 1000.0;
    }

    static String normaliseName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}