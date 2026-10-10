package com.spendio.accounts.support;

import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.spec.AccountFilter;
import com.spendio.sharedkernel.domain.enums.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native ledger fragments (ARCHITECTURE.md D.6). Custom repository fragments are kept
 * out of the {@code ..repository..} package so the ArchUnit "repositories are
 * interfaces" rule holds; the spec uses the same trick for
 * TransactionIndexRepositoryCustom under support/.
 */
@Component
public class AccountBalanceRepositoryImpl implements AccountBalanceRepository {

    private static final String OPENING_MARKER = "opening:%";
    private static final String ACTIVE_LEDGER_ONLY =
            "t.deleted_at IS NULL AND t.status <> 'VOID'"
            + " AND (t.dedupe_key IS NULL OR t.dedupe_key NOT LIKE '" + OPENING_MARKER + "')";

    private final NamedParameterJdbcTemplate jdbc;

    public AccountBalanceRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BalanceLine> balanceLines(Long userId) {
        String sql = """
                SELECT a.id, a.type, a.currency_code, a.opening_balance_minor, COALESCE(SUM(t.signed_amount_minor), 0) AS net
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id AND %s
                WHERE a.user_id = :userId AND a.deleted_at IS NULL
                GROUP BY a.id, a.type, a.currency_code, a.opening_balance_minor
                """.formatted(ACTIVE_LEDGER_ONLY);
        return jdbc.query(sql, Map.of("userId", userId), this::toBalanceLine);
    }

    @Override
    public BalanceLine balanceLine(Long userId, Long accountId) {
        String sql = """
                SELECT a.id, a.type, a.currency_code, a.opening_balance_minor, COALESCE(SUM(t.signed_amount_minor), 0) AS net
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id AND %s
                WHERE a.user_id = :userId AND a.id = :accountId AND a.deleted_at IS NULL
                GROUP BY a.id, a.type, a.currency_code, a.opening_balance_minor
                """.formatted(ACTIVE_LEDGER_ONLY);
        return jdbc.query(sql,
                new MapSqlParameterSource("userId", userId).addValue("accountId", accountId),
                this::toBalanceLine)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<Long, Long> transactionCounts(Long userId) {
        String sql = """
                SELECT a.id, COUNT(t.id) AS cnt
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id AND t.deleted_at IS NULL
                WHERE a.user_id = :userId AND a.deleted_at IS NULL
                GROUP BY a.id
                """;
        return jdbc.query(sql, Map.of("userId", userId), rs -> {
            Map<Long, Long> counts = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                counts.put(rs.getLong(1), rs.getLong(2));
            }
            return counts;
        });
    }

    @Override
    public long countTransactions(Long userId, Long accountId) {
        String sql = "SELECT COUNT(*) FROM transactions t WHERE t.user_id = :userId"
                + " AND t.account_id = :accountId AND t.deleted_at IS NULL";
        Long count = jdbc.queryForObject(sql,
                new MapSqlParameterSource("userId", userId).addValue("accountId", accountId),
                Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<Long> matchingIds(Long userId, AccountFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        StringBuilder where = new StringBuilder("WHERE a.user_id = :userId AND a.deleted_at IS NULL");

        if (filter == null) {
            return jdbc.queryForList(
                    "SELECT a.id FROM accounts a " + where,
                    params, Long.class);
        }
        if (filter.type() != null) {
            where.append(" AND a.type = :type");
            params.addValue("type", filter.type().name());
        }
        if (filter.status() != null) {
            where.append(" AND a.status = :status");
            params.addValue("status", filter.status().name());
        }
        if (filter.currencyCode() != null && !filter.currencyCode().isBlank()) {
            where.append(" AND a.currency_code = :currencyCode");
            params.addValue("currencyCode", filter.currencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String like = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(a.name) LIKE :qLike ESCAPE '\\\\'"
                    + " OR LOWER(COALESCE(a.institution, '')) LIKE :qLike ESCAPE '\\\\')");
            params.addValue("qLike", like);
        }
        if (Boolean.FALSE.equals(filter.includeArchived())) {
            where.append(" AND a.status <> 'ARCHIVED'");
        }
        if (filter.hasTransactions() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM transactions t WHERE t.account_id = a.id"
                    + " AND t.user_id = a.user_id AND t.deleted_at IS NULL) = :hasTransactions");
            params.addValue("hasTransactions", filter.hasTransactions());
        }
        return jdbc.queryForList("SELECT a.id FROM accounts a " + where, params, Long.class);
    }

    @Override
    public List<DailyNet> dailyNets(Long userId, Long accountId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT t.occurred_on AS d, COALESCE(SUM(t.signed_amount_minor), 0) AS net
                FROM transactions t
                WHERE t.user_id = :userId AND t.account_id = :accountId AND %s
                AND t.occurred_on BETWEEN :from AND :to
                GROUP BY t.occurred_on
                ORDER BY t.occurred_on
                """.formatted(ACTIVE_LEDGER_ONLY);
        return jdbc.query(sql,
                new MapSqlParameterSource("userId", userId)
                        .addValue("accountId", accountId)
                        .addValue("from", from)
                        .addValue("to", to),
                (rs, i) -> new DailyNet(rs.getDate("d").toLocalDate(), rs.getLong("net")));
    }

    @Override
    public long netBefore(Long userId, Long accountId, LocalDate date) {
        String sql = """
                SELECT COALESCE(SUM(t.signed_amount_minor), 0)
                FROM transactions t
                WHERE t.user_id = :userId AND t.account_id = :accountId AND %s
                AND t.occurred_on < :date
                """.formatted(ACTIVE_LEDGER_ONLY);
        Long net = jdbc.queryForObject(sql,
                new MapSqlParameterSource("userId", userId)
                        .addValue("accountId", accountId)
                        .addValue("date", date),
                Long.class);
        return net == null ? 0 : net;
    }

    @Override
    public void insertAdjustment(Long userId, Long accountId, String direction, long amountMinor,
            String currencyCode, long baseAmountMinor, LocalDate occurredOn, String description, String dedupeKey) {
        String sql = """
                INSERT INTO transactions
                    (user_id, account_id, category_id, type, direction, amount_minor, currency_code,
                     exchange_rate, base_amount_minor, fx_rate_id, occurred_on, status, description,
                     dedupe_key, deleted_at, created_at, updated_at, created_by, updated_by, version)
                VALUES
                    (:userId, :accountId, NULL, 'ADJUSTMENT', :direction, :amountMinor, :currencyCode,
                     1, :baseAmountMinor, NULL, :occurredOn, 'POSTED', :description,
                     :dedupeKey, NULL, NOW(3), NOW(3), :createdBy, :createdBy, 0)
                """;
        jdbc.update(sql, new MapSqlParameterSource("userId", userId)
                .addValue("accountId", accountId)
                .addValue("direction", direction)
                .addValue("amountMinor", amountMinor)
                .addValue("currencyCode", currencyCode)
                .addValue("baseAmountMinor", baseAmountMinor)
                .addValue("occurredOn", occurredOn)
                .addValue("description", description)
                .addValue("dedupeKey", dedupeKey)
                .addValue("createdBy", userId));
    }

    @Override
    public int softDeleteTransactionsFor(Long userId, Long accountId) {
        String sql = "UPDATE transactions SET deleted_at = NOW(3), updated_at = NOW(3)"
                + " WHERE user_id = :userId AND account_id = :accountId AND deleted_at IS NULL";
        return jdbc.update(sql,
                new MapSqlParameterSource("userId", userId).addValue("accountId", accountId));
    }

    private BalanceLine toBalanceLine(ResultSet rs, int rowNum) throws SQLException {
        return new BalanceLine(rs.getLong("id"), AccountType.valueOf(rs.getString("type")),
                rs.getString("currency_code"),
                rs.getLong("opening_balance_minor"), rs.getLong("net"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}