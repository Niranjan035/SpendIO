# Performance budgets and load tests

ARCHITECTURE.md Phase 15 requires the heaviest endpoints to hold a latency budget and
no endpoint to return an unbounded result set. This folder holds the k6 scenarios that
enforce the budget.

## Budgets

Measured under a 25-VU ramp against the full stack with a seeded database.

| Endpoint | p95 target | p99 ceiling |
|---|---|---|
| `GET /api/v1/dashboard/summary` | 250 ms | 1 000 ms |
| `GET /api/v1/dashboard/recent-transactions` | 200 ms | 800 ms |
| `GET /api/v1/transactions` (paged) | 250 ms | 1 000 ms |
| `GET /api/v1/analytics/category-breakdown` | 300 ms | 1 200 ms |
| `GET /api/v1/analytics/cash-flow` | 300 ms | 1 200 ms |
| Global p95 / p99 | 500 ms | 1 500 ms |

The global thresholds in `k6/config.js` fail the run when breached; the per-endpoint
targets above are the tuning reference and match the SLO buckets in
`backend/src/main/resources/application-prod.yml`.

## Run

```bash
# install: https://grafana.com/docs/k6/latest/set-up/install-k6/
k6 run -e BASE_URL=http://localhost:8080 -e VUS=25 perf/k6/read-paths.js
```

`setup()` registers a throwaway `loadtest+<stamp>@spendio.test` user (which seeds the
default categories) and all VUs replay the read paths with its token. Point
`BASE_URL` at the compose stack (`http://localhost:8080`) or a staging URL.

## Result report

Record the run summary here after each release rehearsal (k6 prints `http_req_duration`
percentiles and the threshold verdict):

```
date:        <yyyy-mm-dd>
commit:      <sha>
environment: <local compose | staging>
VUs:         <n>
http_req_duration: p95=<ms> p99=<ms>
http_req_failed:   <rate>
thresholds:        PASS | FAIL
notes:             <what changed>
```

Bounded-result evidence: every list endpoint is capped by
`PageRequestFactory` (`size` ≤ 100) or an explicit limit
(`AnalyticsRangeValidator.MAX_LIMIT`); analytics windows are range-validated per
granularity, so no endpoint can return an unbounded set.
