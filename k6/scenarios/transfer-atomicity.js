// Challenge §Transfer Atomicity.
// ping_pong:           50 VUs × 1,000 alternating transfers between two hot
//                      accounts; net effect is zero → exact teardown assertion.
// conservation_monitor: one VU continuously re-reads BOTH balances during the
//                      storm; any observed sum ≠ funded total means a partial
//                      transfer was visible → run fails.
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { transfer, fundedAccount, balanceOf } from '../lib/client.js';

const AMOUNT = 100;
const FUND = 500_000;
const TOTAL = 2 * FUND;

const moneyConserved = new Rate('money_conserved');

export const options = {
  scenarios: {
    ping_pong: {
      executor: 'shared-iterations', vus: 50, iterations: 1000,
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
  return { a: fundedAccount(FUND, 'atomic-a'), b: fundedAccount(FUND, 'atomic-b') };
}

export function pingPong(data) {
  // even VUs push A→B, odd VUs push B→A — same amount, so the net is zero
  const [from, to] = __VU % 2 === 0 ? [data.a, data.b] : [data.b, data.a];
  const res = transfer(from, to, AMOUNT, `pp-${__VU}-${__ITER}`);
  check(res, { 'ping-pong applied': (r) => r.status === 201 && r.json('status') === 'APPLIED' });
}

export function monitor(data) {
  moneyConserved.add(balanceOf(data.a) + balanceOf(data.b) === TOTAL);
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
