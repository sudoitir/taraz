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
  const idA = a.json('account_id');
  const idB = createAccount().json('account_id');

  expectApplied(credit(idA, 1000, 'smoke-credit'), 'smoke-credit');
  expectApplied(debit(idA, 300, 'smoke-debit'), 'smoke-debit');
  expectApplied(transfer(idA, idB, 200, 'smoke-transfer'), 'smoke-transfer');

  expectBalanceRead(getBalance(idA), idA, 500);
  expectBalanceRead(getBalance(idB), idB, 200);
}
