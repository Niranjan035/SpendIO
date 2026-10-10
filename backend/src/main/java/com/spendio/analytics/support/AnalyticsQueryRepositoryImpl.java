package com.spendio.analytics.support;

import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.sharedkernel.domain.enums.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The analytics SQL (ARCHITECTURE.md D.14). Mirrors the ledger filters used by
 * {@code AccountBalanceRepositoryImpl}: non-deleted, non-VOID, {@code opening:} rows
 * excluded, transfers excluded from income/expense via the {@code type} column.
 * Lives in {@code support} so the repository package stays interfaces-only (B.3).
 */
@Component
public class AnalyticsQueryRepositoryImpl implements AnalyticsQueryRepository {

    /** Same active-ledger predicate as the accounts balance maths (D.6). */
    private static final String ACTIVE_LEDGER =
            "t.deleted_at IS NULL AND t.status <> 'VOID'"
            + " AND (t.dedupe_key IS NULL OR t.dedupe_key NOT LIKE 'opening:%')";

    private static final String FLOW_SUMS =
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.base_amount_minor ELSE 0 END), 0)"
            + ", COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.base_amount_minor ELSE 0 END), 0)";

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Totals totals(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.base_amount_minor ELSE 0 END), 0) AS income,
                       COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.base_amount_minor ELSE 0 END), 0) AS expense,
                       COALESCE(SUM(CASE WHEN t.type IN ('INCOME','EXPENSE') THEN 1 ELSE 0 END), 0) AS cnt
                FROM transactions t
                WHERE t.user_id = :userId AND t.occurred_on BETWEEN :from AND :to AND %s
                """.formatted(ACTIVE_LEDGER);
        return jdbc.queryForObject(sql, rangeParams(userId, from, to), (rs, i) ->
                new Totals(rs.getLong("income"), rs.getLong("expense"), rs.getLong("cnt")));
    }

    @Override
    public List<DailyFlow> dailyFlow(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT t.occurred_on AS d, %s
                FROM transactions t
                WHERE t.user_id = :userId AND t.occurred_on BETWEEN :from AND :to
                  AND t.type IN ('INCOME','EXPENSE') AND %s
                GROUP BY t.occurred_on
                ORDER BY t.occurred_on
                """.formatted(FLOW_SUMS, ACTIVE_LEDGER);
        return jdbc.query(sql, rangeParams(userId, from, to), (rs, i) ->
                new DailyFlow(rs.getDate("d").toLocalDate(), rs.getLong(2), rs.getLong(3)));
    }

    @Override
    public List<CategoryAmount> byCategory(Long userId, LocalDate from, LocalDate to, String txType,
            int limit, boolean parentOnly) {
        MapSqlParameterSource params = rangeParams(userId, from, to)
                .addValue("limit", limit)
                .addValue("txType", txType);

        String groupColumn;
        String joins;
        String groupBy;
        if (parentOnly) {
            // Children fold into their parent bucket; NULL category stays NULL (uncategorised).
            joins = """
                    LEFT JOIN categories c ON c.id = t.category_id
                    LEFT JOIN categories grp ON grp.id = COALESCE(c.parent_id, c.id)
                    """;
            groupColumn = "grp.id AS category_id, grp.name, grp.color";
            groupBy = "grp.id, grp.name, grp.color";
        } else {
            joins = "LEFT JOIN categories c ON c.id = t.category_id";
            groupColumn = "t.category_id, c.name, c.color";
            groupBy = "t.category_id, c.name, c.color";
        }
        String typeFilter = txType == null || txType.isBlank()
                ? "AND t.type IN ('INCOME','EXPENSE')"
                : "AND t.type = :txType";
        String sql = """
                SELECT %s, COALESCE(SUM(t.base_amount_minor), 0) AS amount, COUNT(t.id) AS cnt
                FROM transactions t
                %s
                WHERE t.user_id = :userId AND t.occurred_on BETWEEN :from AND :to
                  AND %s %s
                GROUP BY %s
                ORDER BY amount DESC
                LIMIT :limit
                """.formatted(groupColumn, joins, ACTIVE_LEDGER, typeFilter, groupBy);
        return jdbc.query(sql, params, this::toCategoryAmount);
    }

    @Override
    public List<MonthTotals> monthlyTotals(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT YEAR(t.occurred_on) AS y, MONTH(t.occurred_on) AS m, %s
                FROM transactions t
                WHERE t.user_id = :userId AND t.occurred_on BETWEEN :from AND :to
                  AND t.type IN ('INCOME','EXPENSE') AND %s
                GROUP BY YEAR(t.occurred_on), MONTH(t.occurred_on)
                ORDER BY y, m
                """.formatted(FLOW_SUMS, ACTIVE_LEDGER);
        return jdbc.query(sql, rangeParams(userId, from, to), (rs, i) ->
                new MonthTotals(rs.getInt("y"), rs.getInt("m"), rs.getLong(3), rs.getLong(4)));
    }

    @Override
    public List<SnapshotPoint> snapshots(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT s.snapshot_date, s.account_id, a.name, a.type, s.balance_base_minor
                FROM account_snapshots s
                JOIN accounts a ON a.id = s.account_id
                WHERE s.user_id = :userId AND s.snapshot_date BETWEEN :from AND :to
                  AND a.deleted_at IS NULL
                ORDER BY s.snapshot_date, s.account_id
                """;
        return jdbc.query(sql, rangeParams(userId, from, to), (rs, i) ->
                new SnapshotPoint(
                        rs.getDate("snapshot_date").toLocalDate(),
                        rs.getLong("account_id"),
                        rs.getString("name"),
                        AccountType.valueOf(rs.getString("type")),
                        rs.getLong("balance_base_minor")));
    }

    @Override
    public List<AccountLine> accountLines(Long userId) {
        String sql = """
                SELECT a.id, a.name, a.type, a.currency_code, a.opening_balance_minor,
                       COALESCE(SUM(t.signed_amount_minor), 0) AS net
                FROM accounts a
                LEFT JOIN transactions t ON t.account_id = a.id AND %s
                WHERE a.user_id = :userId AND a.deleted_at IS NULL
                GROUP BY a.id, a.name, a.type, a.currency_code, a.opening_balance_minor
                """.formatted(ACTIVE_LEDGER);
        return jdbc.query(sql, Map.of("userId", userId), (rs, i) ->
                new AccountLine(rs.getLong("id"), rs.getString("name"),
                        AccountType.valueOf(rs.getString("type")),
                        rs.getString("currency_code"),
                        rs.getLong("opening_balance_minor"), rs.getLong("net")));
    }

    private static MapSqlParameterSource rangeParams(Long userId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("userId", userId)
                .addValue("from", from)
                .addValue("to", to);
    }

    private CategoryAmount toCategoryAmount(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("category_id");
        Long categoryId = rs.wasNull() ? null : id;
        return new CategoryAmount(categoryId, rs.getString("name"), rs.getString("color"),
                rs.getLong("amount"), rs.getLong("cnt"));
    }
}
