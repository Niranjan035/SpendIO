package com.spendio.transactions.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.domain.Transaction;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derived queries and the V6 generated {@code signed_amount_minor} column
 * (ARCHITECTURE.md B.3): owner scoping, dedupe lookups, the duplicate probe and the
 * direction-signed balance maths.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
class TransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private AccountService accountService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private EntityManager em;

    private long alice;
    private long bob;
    private long aliceAccount;
    private long bobAccount;
    private long alpha;
    private long beta;
    private long deleted;
    private long bobAlpha;

    @BeforeEach
    void seedFixture() {
        alice = profileService.create(new CreateUserCommand(
                "alice-repo@spendio.test", "{bcrypt}", "Alice", "Repo", "UTC", "en-US", "USD")).id();
        bob = profileService.create(new CreateUserCommand(
                "bob-repo@spendio.test", "{bcrypt}", "Bob", "Repo", "UTC", "en-US", "USD")).id();
        aliceAccount = seedAccount(alice, "Alice Checking");
        bobAccount = seedAccount(bob, "Bob Checking");

        Account aliceLedger = em.getReference(Account.class, aliceAccount);
        alpha = save(alice, aliceLedger, 1_000L, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED,
                "Alpha", "dup-1");
        beta = save(alice, aliceLedger, 2_000L, LocalDate.of(2026, 6, 1), TransactionStatus.POSTED,
                "Beta", null);
        deleted = save(alice, aliceLedger, 3_000L, LocalDate.of(2026, 6, 2), TransactionStatus.POSTED,
                "Gone", null);
        bobAlpha = save(bob, em.getReference(Account.class, bobAccount), 1_000L,
                LocalDate.of(2026, 6, 1), TransactionStatus.POSTED, "Alpha", "dup-1");

        Transaction doomed = transactions.findById(deleted).orElseThrow();
        doomed.softDelete();
        transactions.save(doomed);
    }

    @Test
    void ownerScopeAndSoftDeleteGovernTheByIdQueries() {
        Optional<Transaction> own = transactions.findByIdAndUserIdAndDeletedAtIsNull(alpha, alice);
        assertThat(own).isPresent();
        assertThat(own.get().getDescription()).isEqualTo("Alpha");

        assertThat(transactions.findByIdAndUserIdAndDeletedAtIsNull(alpha, bob)).isEmpty();
        assertThat(transactions.findByIdAndUserIdAndDeletedAtIsNull(deleted, alice)).isEmpty();
        assertThat(transactions.findByIdAndUserIdAndDeletedAtIsNull(999_999L, alice)).isEmpty();
    }

    @Test
    void dedupeQueriesStayInsideTheOwnersLedger() {
        assertThat(transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(alice, "dup-1")).isTrue();
        assertThat(transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(alice, "missing")).isFalse();
        assertThat(transactions.existsByUserIdAndDedupeKeyAndDeletedAtIsNull(bob, "dup-1")).isTrue();

        assertThat(transactions.findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(alice, "dup-1"))
                .isPresent()
                .get()
                .extracting(Transaction::getId)
                .isEqualTo(alpha);
        assertThat(transactions.findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(bob, "dup-1"))
                .isPresent()
                .get()
                .extracting(Transaction::getId)
                .isEqualTo(bobAlpha);
    }

    @Test
    void probeFindsSameDateAmountAndCurrencyAcrossAccounts() {
        List<Transaction> probe = transactions.findByUserIdAndOccurredOnAndAmountMinorAndCurrencyCodeAndDeletedAtIsNull(
                alice, LocalDate.of(2026, 6, 1), 1_000L, "USD");

        assertThat(probe).extracting(Transaction::getId).containsExactly(alpha);

        assertThat(transactions.findByUserIdAndOccurredOnAndAmountMinorAndCurrencyCodeAndDeletedAtIsNull(
                alice, LocalDate.of(2026, 6, 1), 99_999L, "USD")).isEmpty();
        assertThat(transactions.findByUserIdAndOccurredOnAndAmountMinorAndCurrencyCodeAndDeletedAtIsNull(
                alice, LocalDate.of(2026, 6, 2), 3_000L, "USD")).isEmpty();
    }

    @Test
    void generatedSignedAmountSumsDirectionallyAndSkipsVoid() {
        save(alice, em.getReference(Account.class, aliceAccount), 500L, LocalDate.of(2026, 6, 3),
                TransactionStatus.POSTED, "Out", null, TransactionDirection.OUTFLOW);
        save(alice, em.getReference(Account.class, aliceAccount), 999L, LocalDate.of(2026, 6, 4),
                TransactionStatus.VOID, "Voided", null, TransactionDirection.INFLOW);

        BigDecimal net = (BigDecimal) em.createNativeQuery(
                        "SELECT COALESCE(SUM(signed_amount_minor), 0) FROM transactions "
                                + "WHERE user_id = :userId AND deleted_at IS NULL AND status <> 'VOID'")
                .setParameter("userId", alice)
                .getSingleResult();

        // alpha +1000, beta +2000, outflow -500; deleted and VOID rows excluded.
        assertThat(net.longValue()).isEqualTo(2_500L);
    }

    // --- helpers --------------------------------------------------------------

    private long seedAccount(long owner, String name) {
        return accountService.create(owner, new CreateAccountRequest(
                name, AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
    }

    private long save(long owner, Account account, long amountMinor, LocalDate occurredOn,
            TransactionStatus status, String description, String dedupeKey) {
        return save(owner, account, amountMinor, occurredOn, status, description, dedupeKey,
                TransactionDirection.INFLOW);
    }

    private long save(long owner, Account account, long amountMinor, LocalDate occurredOn,
            TransactionStatus status, String description, String dedupeKey, TransactionDirection direction) {
        Transaction tx = Transaction.create(owner, account, null, TransactionType.ADJUSTMENT, direction,
                amountMinor, "USD", BigDecimal.ONE, amountMinor, null, occurredOn, status, description,
                null, null, null, dedupeKey);
        return transactions.save(tx).getId();
    }
}
