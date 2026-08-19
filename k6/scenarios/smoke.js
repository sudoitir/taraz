// Happy-path gate: if this fails, nothing else is worth running.
import { check } from 'k6';
import { createAccount, credit, debit, transfer, getBalance, txKey } from '../lib/client.js';
import { expectApplied, expectBalanceRead } from '../lib/assert.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

export default function () {
  const a = createAccount();
  check(a, {
    'account created': (r) => r.status === 201,
    'starts at zero': (r) => r.json('balance') === 0,
    'Location header': (r) => !!r.headers['Location'],
  });
  const idA = a.json('accountId');
  const idB = createAccount().json('accountId');

  // Unique per run (not just per VU/iteration within one run): a hardcoded key would collide with
  // processed_transaction's global natural-key uniqueness the moment this scenario runs twice against
  // the same persistent database, silently downgrading every APPLIED assertion to a REPLAY of the
  // previous run's outcome instead of proving anything about this run.
  const creditKey = txKey('smoke-credit');
  const debitKey = txKey('smoke-debit');
  const transferKey = txKey('smoke-transfer');
  expectApplied(credit(idA, 1000, creditKey), creditKey);
  expectApplied(debit(idA, 300, debitKey), debitKey);
  expectApplied(transfer(idA, idB, 200, transferKey), transferKey);

  expectBalanceRead(getBalance(idA), idA, 500);
  expectBalanceRead(getBalance(idB), idB, 200);
}
