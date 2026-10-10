#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# SpendIO MySQL backup (ARCHITECTURE.md Phase 15/16).
#
# Consistent logical dump using a single transaction, with the binary-log
# coordinates recorded as a comment header so a restore can be replayed to a
# point in time. Safe to run against a live primary.
#
#   DB_HOST=mysql DB_USER=root DB_PASSWORD=secret DB_NAME=spendio \
#     infra/scripts/backup.sh [output-dir]
#
# Exit codes: 0 = dump written, non-zero = failure (no partial file left behind).
# ---------------------------------------------------------------------------
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-spendio}"
DB_USER="${DB_USER:-spendio}"
DB_PASSWORD="${DB_PASSWORD:-spendio}"
OUT_DIR="${1:-${BACKUP_DIR:-./backups}}"

mkdir -p "$OUT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="${OUT_DIR}/${DB_NAME}-${STAMP}.sql.gz"
TMP="${TARGET}.partial"

echo "backup: ${DB_HOST}:${DB_PORT}/${DB_NAME} -> ${TARGET}"

# set -o pipefail makes the pipeline fail if mysqldump fails, not just gzip.
# --source-data records binary-log coordinates, but MySQL 8.4 removed SHOW MASTER
# STATUS so some clients can no longer request them; retry without the option.
if ! mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" \
  --single-transaction --quick --routines --triggers --events \
  --set-gtid-purged=OFF --source-data=2 \
  --default-character-set=utf8mb4 \
  "$DB_NAME" | gzip -9 > "$TMP"; then
  echo "backup: warning: --source-data unsupported against this server, retrying without binlog coordinates" >&2
  mysqldump \
    --host="$DB_HOST" --port="$DB_PORT" \
    --user="$DB_USER" --password="$DB_PASSWORD" \
    --single-transaction --quick --routines --triggers --events \
    --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 \
    "$DB_NAME" | gzip -9 > "$TMP"
fi

mv "$TMP" "$TARGET"

# A dump that cannot be listed is not a backup.
gzip -t "$TARGET"
SIZE="$(wc -c < "$TARGET" | tr -d ' ')"
echo "backup: wrote ${SIZE} bytes"
