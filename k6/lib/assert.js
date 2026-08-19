import { check } from 'k6';

export function expectApplied(res, txId) {
  check(res, {
    'status 201': (r) => r.status === 201,
    'status field APPLIED': (r) => r.json('status') === 'APPLIED',
    'transaction_id echoes key': (r) => r.json('transaction_id') === txId,
    'no replay header': (r) => !('Idempotency-Replayed' in r.headers),
    'Location points at balance': (r) => (r.headers['Location'] || '').endsWith('/balance'),
  });
}

export function expectReplayed(res, txId) {
  check(res, {
    'replay status 201': (r) => r.status === 201,
    'status field REPLAYED': (r) => r.json('status') === 'REPLAYED',
    'replay transaction_id echoes key': (r) => r.json('transaction_id') === txId,
    'Idempotency-Replayed: true': (r) => r.headers['Idempotency-Replayed'] === 'true',
  });
}

// Assert on the stable `code` member — never on title/detail (human text).
export function expectProblem(res, status, code) {
  check(res, {
    [`problem status ${status}`]: (r) => r.status === status,
    [`problem code ${code}`]: (r) => r.json('code') === code,
    'problem content type': (r) => (r.headers['Content-Type'] || '').startsWith('application/problem+json'),
    'problem has type/title/detail': (r) => !!r.json('type') && !!r.json('title') && !!r.json('detail'),
  });
}

export function expectFlowIdEcho(res, flowId) {
  check(res, { 'X-Flow-ID echoed': (r) => r.headers['X-Flow-ID'] === flowId });
}

export function expectBalanceRead(res, accountId, expected) {
  check(res, {
    'balance read 200': (r) => r.status === 200,
    'snake_case account_id': (r) => r.json('account_id') === accountId,
    'no-store': (r) => r.headers['Cache-Control'] === 'no-store',
    ...(expected === undefined ? {} : { 'balance exact': (r) => r.json('balance') === expected }),
  });
}
