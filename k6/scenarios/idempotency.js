// Challenge §Idempotency: a transactionId affects the balance exactly once,
// for credit/debit/transfer, under sequential retries AND concurrent duplicates.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { credit, debit, transfer, fundedAccount, createAccount, balanceOf } from '../lib/client.js';
import { expectApplied, expectReplayed } from '../lib/assert.js';

const STORM_VUS = 50;
const CREDIT_AMOUNT = 100;
const DEBIT_AMOUNT = 40;
const TRANSFER_AMOUNT = 25;

const creditApplied = new Counter('credit_applied');
const creditReplayed = new Counter('credit_replayed');
const debitApplied = new Counter('debit_applied');
const debitReplayed = new Counter('debit_replayed');
const transferApplied = new Counter('transfer_applied');
const transferReplayed = new Counter('transfer_replayed');

export const options = {
  scenarios: {
    // Sequential retries: same key ×3 per operation, one VU.
    sequential: { executor: 'per-vu-iterations', vus: 1, iterations: 1, exec: 'sequential' },
    // Concurrent duplicate storms: every VU fires the SAME key at once.
    credit_storm: {
      executor: 'shared-iterations', vus: STORM_VUS, iterations: STORM_VUS,
      exec: 'creditStorm', startTime: '2s',
    },
    debit_storm: {
      executor: 'shared-iterations', vus: STORM_VUS, iterations: STORM_VUS,
      exec: 'debitStorm', startTime: '12s',
    },
    transfer_storm: {
      executor: 'shared-iterations', vus: STORM_VUS, iterations: STORM_VUS,
      exec: 'transferStorm', startTime: '22s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    // Exactly one application per storm; every other request must be a replay.
    credit_applied: ['count==1'],
    debit_applied: ['count==1'],
    transfer_applied: ['count==1'],
    credit_replayed: [`count==${STORM_VUS - 1}`],
    debit_replayed: [`count==${STORM_VUS - 1}`],
    transfer_replayed: [`count==${STORM_VUS - 1}`],
  },
};

export function setup() {
  // A fixed key would collide with processed_transaction's global natural-key uniqueness the moment
  // this scenario runs twice against the same persistent database — every "applied once" assertion
  // below would instead observe a replay of a *previous run's* application. The run-unique prefix keeps
  // the deliberate within-run reuse (same key ×3, or 50 VUs sharing one key) while making each run
  // independent of every other run.
  const runId = uuidv4();
  // amount <= 0 is rejected by design (challenge validation rule) — fundedAccount(0, ...) would call
  // credit(id, 0, ...) and fail with INVALID_AMOUNT. A brand-new account already starts at zero, so a
  // plain createAccount() is the correct way to get a zero-balance transfer destination.
  return {
    runId,
    seq: { a: fundedAccount(1000, 'seq-a'), b: createAccount().json('accountId') },
    storm: {
      a: fundedAccount(100_000, 'storm-a'),
      b: createAccount().json('accountId'),
      creditKey: `storm-credit-${runId}`,
      debitKey: `storm-debit-${runId}`,
      transferKey: `storm-transfer-${runId}`,
    },
  };
}

// --- sequential: same key three times → applied once, replayed twice
export function sequential(data) {
  const { a, b } = data.seq;
  const creditKey = `seq-credit-${data.runId}`;
  const debitKey = `seq-debit-${data.runId}`;
  const transferKey = `seq-transfer-${data.runId}`;

  expectApplied(credit(a, CREDIT_AMOUNT, creditKey), creditKey);
  expectReplayed(credit(a, CREDIT_AMOUNT, creditKey), creditKey);
  expectReplayed(credit(a, CREDIT_AMOUNT, creditKey), creditKey);

  expectApplied(debit(a, DEBIT_AMOUNT, debitKey), debitKey);
  expectReplayed(debit(a, DEBIT_AMOUNT, debitKey), debitKey);
  expectReplayed(debit(a, DEBIT_AMOUNT, debitKey), debitKey);

  expectApplied(transfer(a, b, TRANSFER_AMOUNT, transferKey), transferKey);
  expectReplayed(transfer(a, b, TRANSFER_AMOUNT, transferKey), transferKey);
  expectReplayed(transfer(a, b, TRANSFER_AMOUNT, transferKey), transferKey);

  check(null, {
    'seq credit applied once': () => balanceOf(a) === 1000 + CREDIT_AMOUNT - DEBIT_AMOUNT - TRANSFER_AMOUNT,
    'seq transfer applied once': () => balanceOf(b) === TRANSFER_AMOUNT,
  });
}

// --- concurrent storms: one shared key, 50 simultaneous requests
export function creditStorm(data) {
  const res = credit(data.storm.a, CREDIT_AMOUNT, data.storm.creditKey);
  tally(res, creditApplied, creditReplayed);
}

export function debitStorm(data) {
  const res = debit(data.storm.a, DEBIT_AMOUNT, data.storm.debitKey);
  tally(res, debitApplied, debitReplayed);
}

export function transferStorm(data) {
  const res = transfer(data.storm.a, data.storm.b, TRANSFER_AMOUNT, data.storm.transferKey);
  tally(res, transferApplied, transferReplayed);
}

function tally(res, applied, replayed) {
  check(res, {
    'storm request is 201': (r) => r.status === 201,
    'storm replay header consistent': (r) =>
      (r.json('status') === 'REPLAYED') === (r.headers['Idempotency-Replayed'] === 'true'),
  });
  if (res.status === 201 && res.json('status') === 'APPLIED') applied.add(1);
  if (res.status === 201 && res.json('status') === 'REPLAYED') replayed.add(1);
}

export function teardown(data) {
  const a = balanceOf(data.storm.a);
  const b = balanceOf(data.storm.b);
  check(null, {
    // 100_000 + one credit − one debit − one transfer; the other 49×3 were no-ops.
    'storm credit exactly once': () => a === 100_000 + CREDIT_AMOUNT - DEBIT_AMOUNT - TRANSFER_AMOUNT,
    'storm transfer exactly once': () => b === TRANSFER_AMOUNT,
  });
}
