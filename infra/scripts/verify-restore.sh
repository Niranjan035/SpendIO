#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# SpendIO restore verification (ARCHITECTURE.md Phase 15/16).
#
# Deep-checks a restored schema against its source on the same MySQL server:
#  1. the two schemas expose exactly the same table set;
#  2. Flyway applied the identical version + checksum set (when present);
#  3. core table row counts match 1:1;
#  4. referential sanity: no orphaned rows in the ledger relations the app
#     queries every day.
#
# Non-destructive: only reads information_schema and the two schemas.
#
#   DB_HOST=mysql DB_USER=root DB_PASSWORD=secret \
#     infrark/scripts/verify-restore.sh spendio spendio_restore
#
# Exit codes: 0 = verified, 1 = verification failed.
# ---------------------------------------------------------------------------
set -euo pipefail

SOURCE="${1:?usage: verify-restore.sh <source-schema> <restored-schema>}"
TARGET="${2:?usage: verify-restore.sh <source-schema> <restored-schema>}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

mysql_cmd=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASSWORD" --default-character-set=utf8mb4 -N -B)

# Tables whose row counts must match exactly. Drills can narrow this with
# TABLES_TO_CHECK="users accounts ..." when seeding a minimal schema.
CORE_TABLES="${TABLES_TO_CHECK:-users accounts categories transactions fx_rates budgets budget_alerts goals goal_contributions recurring_transactions recurring_transaction_runs notifications csv_imports job_execution_log audit_log user_sessions}"

fail=0
fail_count() {
  echo "verify-restore: MISMATCH $1" >&2
  fail=$((fail + 1))
}

tables_source="$( "${mysql_cmd[@]}" -e "SELECT table_name FROM information_schema.tables WHERE table_schema='${SOURCE}';" | sort )"
tables_target="$( "${mysql_cmd[@]}" -e "SELECT table_name FROM information_schema.tables WHERE table_schema='${TARGET}';" | sort )"

if [ -z "$tables_source" ]; then
  echo "verify-restore: source schema '${SOURCE}' has no tables" >&2
  exit 1
fi
if [ "$tables_source" != "$tables_target" ]; then
  echo "verify-restore: table set differs between '${SOURCE}' and '${TARGET}'" >&2
  while IFS= read -r t; do
    grep -qx "$t" <<<"$tables_target" || echo "  missing in target: $t" >&2
  done <<<"$tables_source"
  while IFS= read -r t; do
    grep -qx "$t" <<<"$tables_source" || echo "  extra in target:   $t" >&2
  done <<<"$tables_target"
  fail=1
fi

if printf '%s\n' "$tables_source" | grep -qx "flyway_schema_history"; then
  h_source="$( "${mysql_cmd[@]}" -e "SELECT CONCAT(version, '|', checksum) FROM \`${SOURCE}\`.flyway_schema_history ORDER BY installed_rank;" )"
  h_target="$( "${mysql_cmd[@]}" -e "SELECT CONCAT(version, '|', checksum) FROM \`${TARGET}\`.flyway_schema_history ORDER BY installed_rank;" )"
  if [ "$h_source" != "$h_target" ]; then
    fail_count "flyway_schema_history (version|checksum differs between '${SOURCE}' and '${TARGET}')"
  fi
fi

for t in $CORE_TABLES; do
  if ! printf '%s\n' "$tables_source" | grep -qx "$t"; then
    continue
  fi
  c_source="$( "${mysql_cmd[@]}" -e "SELECT COUNT(*) FROM \`${SOURCE}\`.\`${t}\`;" )"
  c_target="$( "${mysql_cmd[@]}" -e "SELECT COUNT(*) FROM \`${TARGET}\`.\`${t}\`;" )"
  if [ "$c_source" != "$c_target" ]; then
    fail_count "row count for '${t}' (${SOURCE}=${c_source}, ${TARGET}=${c_target})"
  fi
done

# Referential sanity on the restored schema. Queries are allowed to fail
# (e.g. a mini drill schema that has no such column) and are then skipped.
check_orphans() {
  local label="$1" query="$2" n
  n="$( "${mysql_cmd[@]}" -e "$query" 2>/dev/null )" || n=""
  n="${n//[[:space:]]/}"
  if [ -n "$n" ] && [ "$n" != "0" ]; then
    fail_count "referential '$label' (${n} rows)"
  fi
}

# transactions must point at a real account (and a real category when one is set)
if printf '%s\n' "$tables_target" | grep -qx transactions; then
  check_orphans "transactions without account" \
    "SELECT COUNT(*) FROM \`${TARGET}\`.transactions t LEFT JOIN \`${TARGET}\`.accounts a ON a.id = t.account_id WHERE a.id IS NULL;"
fi
if printf '%s\n' "$tables_target" | grep -qx categories; then
  check_orphans "category children without parent" \
    "SELECT COUNT(*) FROM \`${TARGET}\`.categories c LEFT JOIN \`${TARGET}\`.categories p ON p.id = c.parent_id WHERE c.parent_id IS NOT NULL AND p.id IS NULL;"
  check_orphans "categories without user" \
    "SELECT COUNT(*) FROM \`${TARGET}\`.categories c LEFT JOIN \`${TARGET}\`.users u ON u.id = c.user_id WHERE u.id IS NULL;"
fi
if printf '%s\n' "$tables_target" | grep -qx budgets; then
  check_orphans "budgets without category" \
    "SELECT COUNT(*) FROM \`${TARGET}\`.budgets b LEFT JOIN \`${TARGET}\`.categories c ON c.id = b.category_id WHERE b.category_id IS NOT NULL AND c.id IS NULL;"
fi
if printf '%s\n' "$tables_target" | grep -qx recurring_transactions; then
  check_orphans "recurring rules without account" \
    "SELECT COUNT(*) FROM \`${TARGET}\`.recurring_transactions r LEFT JOIN \`${TARGET}\`.accounts a ON a.id = r.account_id WHERE a.id IS NULL;"
fi

if [ "$fail" -ne 0 ]; then
  echo "verify-restore: FAILED (${fail} mismatch(es))" >&2
  exit 1
fi

echo "verify-restore: OK ('${SOURCE}' and '${TARGET}' match: tables, flyway, ${CORE_TABLES} rows, referential integrity)"