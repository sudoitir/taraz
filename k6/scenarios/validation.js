// Challenge §Validation + REST error contract.
// Every rejection must return the spec'd problem `code` and leave balances unchanged.
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import {
  createAccount, credit, debit, transfer, getBalance, postRaw, fundedAccount, balanceOf,
} from '../lib/client.js';
import { expectProblem, expectFlowIdEcho, expectBalanceRead } from '../lib/assert.js';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

export default function () {
  const account = fundedAccount(1000, 'validation');
  const other = fundedAccount(500, 'validation');
  const ghost = uuidv4(); // well-formed but never created

  // --- amount <= 0 rejected, balance unchanged (challenge: invalid amount)
  for (const amount of [0, -5]) {
    expectProblem(credit(account, amount, `v-cred-${amount}`), 400, 'INVALID_AMOUNT');
    expectProblem(debit(account, amount, `v-deb-${amount}`), 400, 'INVALID_AMOUNT');
    expectProblem(transfer(account, other, amount, `v-xfer-${amount}`), 400, 'INVALID_AMOUNT');
  }
  check(null, { 'balance unchanged after invalid amounts': () => balanceOf(account) === 1000 });

  // --- unknown account (challenge: unknown account must fail)
  expectProblem(credit(ghost, 100, 'v-ghost-credit'), 404, 'ACCOUNT_NOT_FOUND');
  expectProblem(debit(ghost, 100, 'v-ghost-debit'), 404, 'ACCOUNT_NOT_FOUND');
  expectProblem(transfer(ghost, other, 100, 'v-ghost-src'), 404, 'ACCOUNT_NOT_FOUND');
  expectProblem(transfer(account, ghost, 100, 'v-ghost-dst'), 404, 'ACCOUNT_NOT_FOUND');
  expectProblem(getBalance(ghost), 404, 'ACCOUNT_NOT_FOUND');

  // --- same-account transfer (challenge: clearly defined behavior — we reject)
  expectProblem(transfer(account, account, 100, 'v-same'), 422, 'SAME_ACCOUNT_TRANSFER');
  check(null, { 'balance unchanged after same-account transfer': () => balanceOf(account) === 1000 });

  // --- insufficient funds: operation not executed, balance unchanged
  expectProblem(debit(account, 1200, 'v-nsf-debit'), 422, 'INSUFFICIENT_FUNDS');
  expectProblem(transfer(account, other, 1200, 'v-nsf-xfer'), 422, 'INSUFFICIENT_FUNDS');
  check(null, {
    'balance unchanged after NSF debit': () => balanceOf(account) === 1000,
    'destination unchanged after NSF transfer': () => balanceOf(other) === 500,
  });

  // --- missing Idempotency-Key
  const noKey = postRaw(`/accounts/${account}/credits`, JSON.stringify({ amount: 100 }));
  expectProblem(noKey, 400, 'INVALID_TRANSACTION_ID');

  // --- malformed JSON body
  const broken = postRaw(
    `/accounts/${account}/credits`,
    '{"amount": ',
    { 'Idempotency-Key': 'v-malformed' },
  );
  expectProblem(broken, 400, 'MALFORMED_REQUEST');
  check(null, { 'balance unchanged after malformed request': () => balanceOf(account) === 1000 });

  // --- contract conventions: flow-id echo on success AND error, no-store, snake_case
  const flowId = `flow-${uuidv4()}`;
  expectFlowIdEcho(credit(account, 1, 'v-flow-ok', { 'X-Flow-ID': flowId }), flowId);
  expectFlowIdEcho(debit(account, 10_000, 'v-flow-err', { 'X-Flow-ID': flowId }), flowId);
  expectBalanceRead(getBalance(account), account, 1001);
}
