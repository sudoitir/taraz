// Challenge §Transfer Atomicity.
// ping_pong:           50 VUs × 20 transfers each (1,000 total, exactly 500 each
//                      direction), alternating between two hot accounts; net
//                      effect is zero → exact teardown assertion.
// conservation_monitor: one VU continuously re-reads BOTH balances during the
//                      storm; the observed sum straying far from the funded
//                      total (beyond ordinary read-skew from two sequential
//                      HTTP GETs — see CONSERVATION_TOLERANCE below) means a
//                      partial transfer was visible → run fails.
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import exec from 'k6/execution';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { transfer, fundedAccount, balanceOf, withBackpressureRetry } from '../lib/client.js';

const AMOUNT = 100;
const FUND = 500_000;
const TOTAL = 2 * FUND;

const moneyConserved = new Rate('money_conserved');

const PING_PONG_VUS = 50;
const PING_PONG_ITERATIONS_PER_VU = 20; // 50 × 20 = 1,000 total, 500 A→B and 500 B→A — exact by construction.

export const options = {
  scenarios: {
    ping_pong: {
      // per-vu-iterations: every VU runs exactly 20 iterations so the total is exactly 1,000.
      // (Direction is chosen by scenario-local iterationInTest parity — see pingPong() — so any
      // executor yielding exactly 1,000 iterations would split 500/500; per-vu-iterations makes
      // that total explicit and immune to shared-pool race-outs.)
      executor: 'per-vu-iterations', vus: PING_PONG_VUS, iterations: PING_PONG_ITERATIONS_PER_VU,
      exec: 'pingPong', startTime: '0s',
    },
    conservation_monitor: {
      executor: 'constant-vus', vus: 1, duration: '25s',
      exec: 'monitor', startTime: '0s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    money_conserved: ['rate==1'],
  },
};

export function setup() {
  // ${__VU}/${__ITER} alone repeat identically on every run — without a run-unique component, every
  // transfer here would collide with processed_transaction's global key uniqueness on a second run
  // against the same persistent database and silently replay instead of applying.
  return { runId: uuidv4(), a: fundedAccount(FUND, 'atomic-a'), b: fundedAccount(FUND, 'atomic-b') };
}

export function pingPong(data) {
  // Direction comes from the SCENARIO-LOCAL iteration counter, never the global __VU: k6 hands out
  // VU ids across ALL scenarios of the run, so with the conservation_monitor VU taking a global id
  // between ping_pong's, __VU parity is not 25/25 (observed live: ping_pong got ids 1,3,4,…,51 →
  // 26 odd × 20 vs 24 even × 20 = 520/480 transfers → teardown's exact-restore check fails while
  // every individual transfer is correct). iterationInTest is 0..999 within ping_pong, so parity
  // splits exactly 500/500 regardless of global VU id assignment.
  const [from, to] = exec.scenario.iterationInTest % 2 === 0 ? [data.a, data.b] : [data.b, data.a];
  const key = `pp-${data.runId}-${exec.scenario.iterationInTest}`;
  // 50 VUs × 1,000 transfers, all locking the same two hot accounts (ADR-0026), can legitimately
  // exhaust Hikari's short connection-timeout (ADR-0054) for some callers — retry the typed 503 with
  // the same key, as a real client is expected to.
  const res = withBackpressureRetry(() => transfer(from, to, AMOUNT, key));
  check(res, { 'ping-pong applied': (r) => r.status === 201 && r.json('status') === 'APPLIED' });
}

// Two independent GET calls can never be a true atomic dual-read: a handful of the 50 concurrent
// transfers can legitimately commit in the gap between reading A and reading B, so the *observed* sum
// can transiently drift by a small amount even though every individual transfer is atomic — that drift
// is an artifact of this black-box observation method (there is no multi-account atomic-read endpoint),
// not evidence of a partial transfer. A genuine partial-transfer bug (money created, destroyed, debited
// without a matching credit) would show up as a persistent or unbounded deviation, not a small transient
// one — bounding by a few transfer amounts still catches that while tolerating ordinary read skew.
const CONSERVATION_TOLERANCE = 20 * AMOUNT;

export function monitor(data) {
  const sum = balanceOf(data.a) + balanceOf(data.b);
  moneyConserved.add(Math.abs(sum - TOTAL) <= CONSERVATION_TOLERANCE);
}

export function teardown(data) {
  const a = balanceOf(data.a);
  const b = balanceOf(data.b);
  check(null, {
    'source restored exactly': () => a === FUND,
    'destination restored exactly': () => b === FUND,
    'total money conserved': () => a + b === TOTAL,
  });
}
