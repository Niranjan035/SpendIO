package com.spendio.imports.matcher;

import com.spendio.categories.domain.Category;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Fuzzy category matching (ARCHITECTURE.md D.12 step 4): same scoring as
 * {@link AccountMatcher}, restricted to the transaction type's own category tree.
 */
public final class CategoryMatcher {

    private CategoryMatcher() {
    }

    public record Candidate(Long id, String name, Category.Type type) {
    }

    public static Optional<AccountMatcher.Match> match(String text, Category.Type type,
            List<Candidate> candidates) {
        List<AccountMatcher.Candidate> scoped = candidates.stream()
                .filter(candidate -> candidate.type() == type)
                .map(candidate -> new AccountMatcher.Candidate(candidate.id(), candidate.name()))
                .toList();
        return AccountMatcher.match(text, scoped);
    }
}