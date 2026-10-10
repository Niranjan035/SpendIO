#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# SpendIO MySQL restore / restore drill (ARCHITECTURE.md Phase 15/16).
#
# Restores a gzipped dump produced by backup.sh into a target database.
# With ALLOW_DROP=1 the target schema is dropped first (destructive); the
# default restores into an existing/empty schema so a drill can point at a
# scratch database without touching production.
#
#   DB_HOST=mysql DB_USER=root DB_PASSWORD=secret DB_NAME=spendio_restore \
#     infra/scripts/restore.sh backups/spendio-20260201T000000Z.sql.gz
#
# Exit codes: 0 = restore applied and verified, non-zero = failure.
# ---------------------------------------------------------------------------
set -euo pipefail

DUMP="${1:?usage: restore.sh <dump.sql.gz>}"
[ -f "$DUMP" ] || { echo "restore: no such file: $DUMP" >&2; exit 2; }

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-spendio_restore}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
ALLOW_DROP="${ALLOW_DROP:-0}"

echo "restore: ${DUMP} -> ${DB_HOST}:${DB_PORT}/${DB_NAME}"

mysql_cmd=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASSWORD" --default-character-set=utf8mb4)

if [ "$ALLOW_DROP" = "1" ]; then
  "${mysql_cmd[@]}" -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
else
  "${mysql_cmd[@]}" -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
fi

gunzip -c "$DUMP" | "${mysql_cmd[@]}" "$DB_NAME"

# Verify the restore by counting the tables the application depends on.
TABLES="$( "${mysql_cmd[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';" )"
USERS="$( "${mysql_cmd[@]}" -N -B -e "SELECT COUNT(*) FROM \`${DB_NAME}\`.users;" )"
echo "restore: ${TABLES} tables, ${USERS} user rows"

if [ "${TABLES:-0}" -lt 5 ]; then
  echo "restore: verification failed (expected the migrated schema) - the dump looks empty" >&2
  exit 1
fi

echo "restore: OK"
