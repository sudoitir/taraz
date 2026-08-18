# Challenge rule: Testing

Source: `docs/Coding_Challenge_V2_English.md` → Testing.

Tests are a core, graded part of the challenge. Minimum coverage:

## Idempotency tests

- Credit: `balance = 1000`, three × `credit(A, 100, "TX-1")` → final balance **1100**.
- Equivalent idempotency tests for **debit and transfer** as well.

## Concurrency tests

- At least **one single-account** concurrent scenario and **one multi-account** scenario.
- Reference shape: `balance = 100,000`, 1,000 concurrent operations, assert exact expected final balance.
- The test must demonstrate correctness under concurrency (deterministic assertion, not "no exception").
- Where possible, design a test that **raises the probability of race conditions** (e.g. many threads, barrier-synchronized start, interleaving-friendly operations).

## Also cover

- Insufficient-funds debit (balance unchanged), `amount <= 0` rejection, unknown-account errors, same-account transfer behavior, transfer atomicity under concurrency.

## Command

- Tests must run with one standard command: `./mvnw test`.
