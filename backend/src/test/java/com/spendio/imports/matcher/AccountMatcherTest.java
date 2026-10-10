package com.spendio.imports.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.imports.matcher.AccountMatcher.Candidate;
import com.spendio.imports.matcher.AccountMatcher.Match;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Fuzzy account matching (ARCHITECTURE.md D.12 step 4): exact wins over fuzzy, the
 * normalized-equality tier, and the 0.75 Jaccard floor that keeps half-matches out.
 */
class AccountMatcherTest {

    private static final Candidate CHECKING = new Candidate(1L, "Chase Checking");
    private static final Candidate SAVINGS = new Candidate(2L, "Ally Savings");
    private static final Candidate CREDIT = new Candidate(3L, "Chase Freedom Card");

    @Test
    void exactNameWinsImmediately() {
        Optional<Match> match = AccountMatcher.match("chase checking",
                List.of(SAVINGS, new Candidate(1L, "chase checking"), CREDIT));

        assertThat(match).contains(new Match(1L, "EXACT", BigDecimal.ONE));
    }

    @Test
    void caseAndPunctuationDifferencesAreNormalizedEquality() {
        Optional<Match> match = AccountMatcher.match("CHASE   Checking!!",
                List.of(CHECKING, SAVINGS));

        assertThat(match).isPresent();
        assertThat(match.get().candidateId()).isEqualTo(1L);
        assertThat(match.get().matchedBy()).isEqualTo("NORMALIZED");
        assertThat(match.get().confidence()).isEqualByComparingTo("0.98");
    }

    @Test
    void tokenOverlapAboveFloorMatchesFuzzy() {
        // intersection {chase, freedom, card} / union {chase, freedom, card, credit} = 0.75
        Optional<Match> match = AccountMatcher.match("Chase Freedom Card",
                List.of(SAVINGS, new Candidate(3L, "Chase Freedom Credit Card")));

        assertThat(match).isPresent();
        assertThat(match.get().matchedBy()).isEqualTo("FUZZY");
        assertThat(match.get().confidence()).isEqualByComparingTo("0.75");
    }

    @Test
    void overlapBelowFloorIsEmpty() {
        assertThat(AccountMatcher.match("groceries", List.of(CHECKING, SAVINGS))).isEmpty();
        assertThat(AccountMatcher.match("Chase Freedom Card", List.of(SAVINGS))).isEmpty();
    }

    @Test
    void bestScoreWinsAcrossCandidates() {
        // 0.667 for "Chase Freedom" vs 0.75 for "Chase Freedom Credit Card"
        Optional<Match> match = AccountMatcher.match("Chase Freedom Credit",
                List.of(new Candidate(5L, "Chase Freedom"),
                        new Candidate(4L, "Chase Freedom Credit Card")));

        assertThat(match).contains(new Match(4L, "FUZZY", BigDecimal.valueOf(0.75)));
    }

    @Test
    void blankTextOrNoCandidatesIsEmpty() {
        assertThat(AccountMatcher.match(null, List.of(CHECKING))).isEmpty();
        assertThat(AccountMatcher.match("   ", List.of(CHECKING))).isEmpty();
        assertThat(AccountMatcher.match("Chase Checking", null)).isEmpty();
        assertThat(AccountMatcher.match("Chase Checking", List.of())).isEmpty();
    }

    @Test
    void jaccardMath() {
        assertThat(AccountMatcher.jaccard(AccountMatcher.tokens("a b c"),
                AccountMatcher.tokens("a b c"))).isEqualTo(1.0);
        assertThat(AccountMatcher.jaccard(AccountMatcher.tokens("a b"),
                AccountMatcher.tokens("a c"))).isEqualTo(1.0 / 3.0);
        assertThat(AccountMatcher.jaccard(AccountMatcher.tokens("a b"),
                AccountMatcher.tokens(""))).isEqualTo(0.0);
    }
}