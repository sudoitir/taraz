import http from 'k6/http';
import { sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { BASE_URL } from '../config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function createAccount() {
  return http.post(`${BASE_URL}/accounts`, null, { headers: JSON_HEADERS });
}

export function getBalance(accountId) {
  return http.get(`${BASE_URL}/accounts/${accountId}/balance`);
}

export function credit(accountId, amount, txKey, extraHeaders = {}) {
  return http.post(
    `${BASE_URL}/accounts/${accountId}/credits`,
    JSON.stringify({ amount }),
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': txKey, ...extraHeaders } },
  );
}

export function debit(accountId, amount, txKey, extraHeaders = {}) {
  return http.post(
    `${BASE_URL}/accounts/${accountId}/debits`,
    JSON.stringify({ amount }),
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': txKey, ...extraHeaders } },
  );
}

export function transfer(sourceId, destinationId, amount, txKey, extraHeaders = {}) {
  return http.post(
    `${BASE_URL}/transfers`,
    JSON.stringify({ sourceAccountId: sourceId, destinationAccountId: destinationId, amount }),
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': txKey, ...extraHeaders } },
  );
}

export function postRaw(path, body, headers = {}) {
  return http.post(`${BASE_URL}${path}`, body, { headers: { ...JSON_HEADERS, ...headers } });
}

// Unique key per operation — use for every op except the duplicate-storm tests,
// which intentionally share one key across VUs. __VU/__ITER only exist inside a VU's exec function;
// setup()/teardown() run outside that context, where they're undefined — guard so fundedAccount() stays
// callable from setup() (every scenario's account-funding step) without a ReferenceError.
export function txKey(tag) {
  const vu = typeof __VU !== 'undefined' ? __VU : 'setup';
  const iter = typeof __ITER !== 'undefined' ? __ITER : 0;
  return `${tag}-${vu}-${iter}-${uuidv4()}`;
}

// Fund an account in one shot; returns the account id.
export function fundedAccount(amount, tag) {
  const id = createAccount().json('accountId');
  const res = credit(id, amount, txKey(`fund-${tag}`));
  if (res.status !== 201) throw new Error(`funding failed for ${id}: ${res.status} ${res.body}`);
  return id;
}

// ADR-0054: a heavy synthetic burst against one account row can legitimately exhaust the connection
// pool and get a typed 503 CONCURRENCY_CONFLICT — deliberate backpressure, not an error. The operation
// never reached the database, so retrying with the *same* Idempotency-Key is both safe and correct
// (ADR-0041); a well-behaved client is expected to do exactly this.
export function withBackpressureRetry(op, maxAttempts = 20) {
  let res = op();
  let attempt = 1;
  while (res.status === 503 && attempt < maxAttempts) {
    sleep(0.02 * attempt);
    res = op();
    attempt++;
  }
  return res;
}

export function balanceOf(accountId) {
  const res = getBalance(accountId);
  if (res.status !== 200) throw new Error(`balance read failed for ${accountId}: ${res.status}`);
  return res.json('balance');
}
