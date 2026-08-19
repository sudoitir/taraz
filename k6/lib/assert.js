import { check } from 'k6';

export function expectApplied(res, txId) {
  check(res, {
    'status 201': (r) => r.status === 201,
    'status field APPLIED': (r) => r.json('status') === 'APPLIED',
    'transactionId echoes key': (r) => r.json('transactionId') === txId,
    'no replay header': (r) => !('Idempotency-Replayed' in r.headers),
    'Location points at balance': (r) => (r.headers['Location'] || '').endsWith('/balance'),
  });
}

export function expectReplayed(res, txId) {
  check(res, {
    'replay status 201': (r) => r.status === 201,
    'status field REPLAYED': (r) => r.json('status') === 'REPLAYED',
    'replay transactionId echoes key': (r) => r.json('transactionId') === txId,
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

export function expectCorrelationIdEcho(res, correlationId) {
  check(res, { 'X-Correlation-ID echoed': (r) => r.headers['X-Correlation-Id'] === correlationId });
}

export function expectBalanceRead(res, accountId, expected) {
  check(res, {
    'balance read 200': (r) => r.status === 200,
    'accountId matches': (r) => r.json('accountId') === accountId,
    'no-store': (r) => r.headers['Cache-Control'] === 'no-store',
    ...(expected === undefined ? {} : { 'balance exact': (r) => r.json('balance') === expected }),
  });
}
