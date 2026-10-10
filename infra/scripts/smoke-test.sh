#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# SpendIO post-deploy smoke test (ARCHITECTURE.md Phase 16).
#
# Verifies the deployed stack answers the liveness/readiness probes and that
# the API is reachable, without needing credentials. Runs unchanged in CI
# (against the local compose stack) and after a production deploy.
#
#   BASE_URL=https://app.example.com infra/scripts/smoke-test.sh
#
# Exit codes: 0 = every check passed, non-zero = first failing check.
# ---------------------------------------------------------------------------
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ATTEMPTS="${SMOKE_ATTEMPTS:-30}"
SLEEP="${SMOKE_SLEEP_SECONDS:-2}"

pass=0
fail=0

check() {
  local name="$1" url="$2" expect="${3:-200}"
  local code
  for _ in $(seq 1 "$ATTEMPTS"); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)"
    [ "$code" = "$expect" ] && break
    sleep "$SLEEP"
  done
  if [ "$code" = "$expect" ]; then
    echo "  PASS ${name} (${expect})"
    pass=$((pass + 1))
  else
    echo "  FAIL ${name} (got ${code}, want ${expect}) ${url}" >&2
    fail=$((fail + 1))
  fi
}

echo "smoke: ${BASE_URL}"
check "liveness"  "${BASE_URL}/actuator/health/liveness"  200
check "readiness" "${BASE_URL}/actuator/health/readiness" 200
check "health"    "${BASE_URL}/actuator/health"           200
# Unauthenticated request must be a clean 401 problem+json, proving the API is up.
check "api unauth" "${BASE_URL}/api/v1/users/me" 401

echo "smoke: ${pass} passed, ${fail} failed"
[ "$fail" -eq 0 ]
