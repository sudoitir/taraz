// Challenge §Concurrency — single account.
// race_pair:      1000 balance, two concurrent debit(700) → exactly one wins → 300.
// thousand_debits: the challenge's reference shape — 100,000 balance, 1,000
//                  concurrent debit(100) → exactly 0. No pacing: maximum race probability.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { debit, fundedAccount, balanceOf, withBackpressureRetry } from '../lib/client.js';

const drained = new Counter('debit_applied_count');
const rejected = new Counter('debit_insufficient_funds');

export const options = {
  scenarios: {
    race_pair: {
      executor: 'shared-iterations', vus: 2, iterations: 2,
      exec: 'racePair', startTime: '0s',
    },
    thousand_debits: {
      executor: 'shared-iterations', vus: 100, iterations: 1000,
      exec: 'thousandDebits', startTime: '5s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    // 100,000 / 100 = exactly 1,000 debits succeed, never more.
    debit_applied_count: ['count==1000'],
    debit_insufficient_funds: ['count==0'],
  },
};

export function setup() {
  // ${__VU}/${__ITER} alone repeat identically on every run — without a run-unique component, every
  // debit here would collide with processed_transaction's global key uniqueness on a second run against
  // the same persistent database and silently replay instead of debiting the fresh accounts below.
  return {
    runId: uuidv4(),
    raceAccount: fundedAccount(1000, 'race'),
    hotAccount: fundedAccount(100_000, 'hot'),
  };
}

export function racePair(data) {
  const key = `race-${data.runId}-${__VU}`;
  const res = withBackpressureRetry(() => debit(data.raceAccount, 700, key));
  check(res, {
    // one wins (201), one is rejected with insufficient funds — nothing else is valid
    'race outcome is win-or-NSF': (r) =>
      r.status === 201 || (r.status === 422 && r.json('code') === 'INSUFFICIENT_FUNDS'),
  });
}

export function thousandDebits(data) {
  const key = `hot-${data.runId}-${__VU}-${__ITER}`;
  // 100 VUs racing 1,000 debits fully serializes behind this one account's row lock (ADR-0026) — a
  // deliberately extreme burst that can legitimately exhaust Hikari's short connection-timeout
  // (ADR-0054) for some callers. withBackpressureRetry retries the typed 503 with the same key, the
  // way a real client is expected to, so this proves "every debit eventually lands exactly once" rather
  // than asserting away the backpressure the ADR intentionally introduces.
  const res = withBackpressureRetry(() => debit(data.hotAccount, 100, key));
  if (res.status === 201) drained.add(1);
  else if (res.status === 422 && res.json('code') === 'INSUFFICIENT_FUNDS') rejected.add(1);
  check(res, {
    'never negative: failure is only INSUFFICIENT_FUNDS': (r) =>
      r.status === 201 || (r.status === 422 && r.json('code') === 'INSUFFICIENT_FUNDS'),
  });
}

export function teardown(data) {
  check(null, {
    'race pair: exactly one debit landed': () => balanceOf(data.raceAccount) === 300,
    'thousand debits: drained to exactly zero': () => balanceOf(data.hotAccount) === 0,
  });
}
