## MODIFIED Requirements

### Requirement: Validation proofs

`validation.js` SHALL assert every validation rule of the challenge with balance-unchanged proofs:
`amount <= 0` rejected on credits/debits/transfers, unknown account rejected on all operations,
same-account transfer rejected, missing `Idempotency-Key` rejected, malformed JSON rejected, and
insufficient-funds debit rejected — each with the status and problem `code` defined by the `rest-api`
spec, plus `application/problem+json` content type, camelCase fields, `Cache-Control: no-store` on
balance reads, and `X-Correlation-ID` echo on success and error responses. (ADR-0056/0058: the k6 suite
was written against the pre-amendment contract — `X-Flow-ID` and snake_case fields — and is updated here
to match the header rename and the JSON-naming switch, both amendments to this same change.)

#### Scenario: Every rejection leaves balances unchanged

- **WHEN** `validation.js` runs against the service
- **THEN** each invalid request returns the spec'd status and `code`, and a balance re-read after each
  rejection equals the pre-request balance
