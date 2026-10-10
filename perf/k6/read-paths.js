// ---------------------------------------------------------------------------
// SpendIO read-path load test (ARCHITECTURE.md Phase 15).
//
// Exercises the heaviest read endpoints behind a real bearer token: the
// dashboard summary, the paged transaction list, and the analytics rollups.
// setup() registers a fresh user (which seeds default categories) and returns
// its access token; every VU then replays the read paths.
//
//   k6 run -e BASE_URL=http://localhost:8080 -e VUS=25 perf/k6/read-paths.js
//
// Thresholds are defined in config.js and fail the run when breached.
// ---------------------------------------------------------------------------
import http from 'k6/http'
import { check } from 'k6'
import { BASE_URL, options } from './config.js'

export const options = options(Number(__ENV.VUS || 25))

function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } }
}

export function setup() {
  const stamp = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`
  const body = JSON.stringify({
    email: `loadtest+${stamp}@spendio.test`,
    password: `L0adTest-${stamp}-aA!`,
    firstName: 'Load',
    lastName: 'Test',
    baseCurrencyCode: 'USD',
  })
  const res = http.post(`${BASE_URL}/api/v1/auth/register`, body, {
    headers: { 'Content-Type': 'application/json' },
  })
  check(res, { 'registered': (r) => r.status === 201 || r.status === 200 })
  return { token: res.json('accessToken') }
}

const DAY = 24 * 60 * 60 * 1000
const iso = (ms) => new Date(ms).toISOString().slice(0, 10)

export default function (data) {
  const auth = authHeaders(data.token)
  const to = Date.now()
  const from = to - 30 * DAY

  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/dashboard/summary`, null, auth],
    ['GET', `${BASE_URL}/api/v1/dashboard/recent-transactions`, null, auth],
    ['GET', `${BASE_URL}/api/v1/transactions?page=0&size=25`, null, auth],
    ['GET', `${BASE_URL}/api/v1/analytics/category-breakdown`, null, auth],
    ['GET', `${BASE_URL}/api/v1/analytics/cash-flow?from=${iso(from)}&to=${iso(to)}&granularity=MONTH`, null, auth],
  ])

  check(responses, {
    'dashboard summary 200': (r) => r[0].status === 200,
    'recent transactions 200': (r) => r[1].status === 200,
    'transaction list 200': (r) => r[2].status === 200,
    'category breakdown 200': (r) => r[3].status === 200,
    'cash flow 200': (r) => r[4].status === 200,
  })
}
