// Sustained-load benchmark — measures, never proves correctness (the other six scenarios do that).
// 200 pre-funded accounts, constant arrival rate, weighted mix: 40% credit, 30% debit, 20% transfer,
// 10% balance read. Every operation gets a unique idempotency key and its response is checked, so a
// correctness regression fails the run instead of hiding inside an average. Funding is huge so debits
// never legitimately reject — any non-201/non-200 is a defect signal, not workload.
import { check } from 'k6';
import exec from 'k6/execution';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { createAccount, credit, debit, transfer, getBalance } from '../lib/client.js';

const ACCOUNTS = 200;
const FUND = 1_000_000_000_000; // debits never run dry during the benchmark

export const options = {
  scenarios: {
    benchmark: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 500), // requests per second
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<2000'],
  },
};

export function setup() {
  const runId = uuidv4();
  const accounts = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const id = createAccount().json('accountId');
    const res = credit(id, FUND, `bm-fund-${runId}-${i}`);
    if (res.status !== 201) throw new Error(`funding failed for ${id}: ${res.status} ${res.body}`);
    accounts.push(id);
  }
  return { runId, accounts };
}

export default function (data) {
  const i = exec.scenario.iterationInTest;
  const key = `bm-${data.runId}-${i}`;
  const a = data.accounts[i % ACCOUNTS];
  const b = data.accounts[(i * 7 + 13) % ACCOUNTS]; // deterministic, distinct from a (200 ∤ 7i+13-i for these i)
  const amount = 1 + (i % 1000);
  const kind = i % 10;

  let res;
  let ok;
  if (kind < 4) {
    res = credit(a, amount, key);
    ok = res.status === 201 && res.json('status') === 'APPLIED';
  } else if (kind < 7) {
    res = debit(a, amount, key);
    ok = res.status === 201 && res.json('status') === 'APPLIED';
  } else if (kind < 9) {
    res = transfer(a, b, amount, key);
    ok = res.status === 201 && res.json('status') === 'APPLIED';
  } else {
    res = getBalance(a);
    ok = res.status === 200;
  }
  check(res, { 'op succeeded': () => ok });
}
