import http from 'k6/http';
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
    JSON.stringify({ source_account_id: sourceId, destination_account_id: destinationId, amount }),
    { headers: { ...JSON_HEADERS, 'Idempotency-Key': txKey, ...extraHeaders } },
  );
}

export function postRaw(path, body, headers = {}) {
  return http.post(`${BASE_URL}${path}`, body, { headers: { ...JSON_HEADERS, ...headers } });
}

// Unique key per operation — use for every op except the duplicate-storm tests,
// which intentionally share one key across VUs.
export function txKey(tag) {
  return `${tag}-${__VU}-${__ITER}-${uuidv4()}`;
}

// Fund an account in one shot; returns the account id.
export function fundedAccount(amount, tag) {
  const id = createAccount().json('account_id');
  const res = credit(id, amount, txKey(`fund-${tag}`));
  if (res.status !== 201) throw new Error(`funding failed for ${id}: ${res.status} ${res.body}`);
  return id;
}

export function balanceOf(accountId) {
  const res = getBalance(accountId);
  if (res.status !== 200) throw new Error(`balance read failed for ${accountId}: ${res.status}`);
  return res.json('balance');
}
