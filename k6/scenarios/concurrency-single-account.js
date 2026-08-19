// Challenge §Concurrency — single account.
// race_pair:      1000 balance, two concurrent debit(700) → exactly one wins → 300.
// thousand_debits: the challenge's reference shape — 100,000 balance, 1,000
//                  concurrent debit(100) → exactly 0. No pacing: maximum race probability.
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { debit, fundedAccount, balanceOf } from '../lib/client.js';

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
  return {
    raceAccount: fundedAccount(1000, 'race'),
    hotAccount: fundedAccount(100_000, 'hot'),
  };
}

export function racePair(data) {
  const res = debit(data.raceAccount, 700, `race-${__VU}`);
  check(res, {
    // one wins (201), one is rejected with insufficient funds — nothing else is valid
    'race outcome is win-or-NSF': (r) =>
      r.status === 201 || (r.status === 422 && r.json('code') === 'INSUFFICIENT_FUNDS'),
  });
}

export function thousandDebits(data) {
  const res = debit(data.hotAccount, 100, `hot-${__VU}-${__ITER}`);
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
