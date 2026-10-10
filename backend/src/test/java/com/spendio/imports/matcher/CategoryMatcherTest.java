package com.spendio.imports.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.categories.domain.Category;
import com.spendio.imports.matcher.CategoryMatcher.Candidate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Category matching stays inside the transaction type's own category tree. */
class CategoryMatcherTest {

    private static final Candidate GROCERIES = new Candidate(10L, "Groceries", Category.Type.EXPENSE);
    private static final Candidate SALARY = new Candidate(20L, "Salary", Category.Type.INCOME);

    @Test
    void matchesInsideTheSameType() {
        Optional<AccountMatcher.Match> match = CategoryMatcher.match("groceries",
                Category.Type.EXPENSE, List.of(GROCERIES, SALARY));

        assertThat(match).isPresent();
        assertThat(match.get().candidateId()).isEqualTo(10L);
        assertThat(match.get().matchedBy()).isEqualTo("EXACT");
    }

    @Test
    void candidateOfTheOtherTypeIsInvisible() {
        Optional<AccountMatcher.Match> match = CategoryMatcher.match("Salary",
                Category.Type.EXPENSE, List.of(SALARY));

        assertThat(match).isEmpty();
    }

    @Test
    void noCandidatesOfThatTypeIsEmpty() {
        assertThat(CategoryMatcher.match("anything", Category.Type.EXPENSE, List.of())).isEmpty();
        assertThat(CategoryMatcher.match(null, Category.Type.INCOME, List.of(SALARY))).isEmpty();
    }

    @Test
    void fuzzyFollowsTheSameFloor() {
        Candidate wholeFoods = new Candidate(30L, "Whole Foods Market NYC", Category.Type.EXPENSE);

        assertThat(CategoryMatcher.match("Whole Foods Market", Category.Type.EXPENSE, List.of(wholeFoods)))
                .isPresent();
        assertThat(CategoryMatcher.match("Whole Foods", Category.Type.EXPENSE, List.of(wholeFoods)))
                .isEmpty();
    }
}