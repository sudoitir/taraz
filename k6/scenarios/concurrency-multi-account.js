// Challenge §Concurrency — independent accounts must not block each other.
// Each VU owns a private account pair no other VU touches, and ping-pongs a
// fixed amount between them. Proof: every pair's sum is conserved exactly and
// the whole run completes within the latency budget — a global lock would
// serialize 25 VUs and blow the thresholds.
import { check } from 'k6';
import { transfer, fundedAccount, balanceOf } from '../lib/client.js';

const VUS = 25;
const ITERS = 50;
const AMOUNT = 10;
const FUND = 10_000;

export const options = {
  scenarios: {
    independent_pairs: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERS,
      maxDuration: '2m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    // 1,250 transfers on 50 disjoint accounts; p95 per request must stay low.
    http_req_duration: ['p(95)<500'],
    iterations: [`count==${VUS * ITERS}`],
  },
};

export function setup() {
  const pairs = [];
  for (let i = 0; i < VUS; i++) {
    pairs.push({ a: fundedAccount(FUND, `pair${i}-a`), b: fundedAccount(FUND, `pair${i}-b`) });
  }
  return { pairs };
}

export default function (data) {
  const pair = data.pairs[__VU - 1]; // this VU's private pair — nobody else touches it
  const [from, to] = __ITER % 2 === 0 ? [pair.a, pair.b] : [pair.b, pair.a];
  const res = transfer(from, to, AMOUNT, `pair-${__VU}-${__ITER}`);
  check(res, {
    'independent transfer applied': (r) => r.status === 201 && r.json('status') === 'APPLIED',
  });
}

export function teardown(data) {
  for (const pair of data.pairs) {
    const sum = balanceOf(pair.a) + balanceOf(pair.b);
    check(sum, {
      [`pair sum conserved (${pair.a.slice(0, 8)}…)`]: (s) => s === 2 * FUND,
    });
  }
}
