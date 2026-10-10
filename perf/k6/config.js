// Shared k6 options and latency budgets (ARCHITECTURE.md Phase 15).
// Budgets mirror the SLO buckets in application-prod.yml and the alert rules in
// infra/monitoring/alert.rules.yml. Exceeding a threshold fails the run.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
export const RAMP = __ENV.RAMP || '30s'

export function options(vus) {
  return {
    scenarios: {
      read_paths: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: RAMP, target: vus },
          { duration: '1m', target: vus },
          { duration: '30s', target: 0 },
        ],
      },
    },
    thresholds: {
      // p95 within the 500ms budget; p99 within 1.5s (Phase 15 done-when).
      http_req_duration: ['p(95)<500', 'p(99)<1500'],
      http_req_failed: ['rate<0.01'],
      checks: ['rate>0.99'],
    },
  }
}
