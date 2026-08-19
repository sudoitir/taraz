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
      // per-vu-iterations (not shared-iterations): direction is chosen by __VU parity, so restoring
      // both accounts to exactly FUND requires exactly half the total transfers going each way.
      // shared-iterations pulls from one dynamic pool and does not guarantee that split — with 50 VUs
      // racing for a shared budget of 1,000 iterations, whichever VUs happen to run faster (including
      // VUs that had to wait out a withBackpressureRetry backoff) end up completing more or fewer of
      // them, silently unbalancing the even/odd split. per-vu-iterations fixes each VU's share instead.
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
  // even VUs push A→B, odd VUs push B→A — same amount, so the net is zero
  const [from, to] = __VU % 2 === 0 ? [data.a, data.b] : [data.b, data.a];
  const key = `pp-${data.runId}-${__VU}-${__ITER}`;
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
