# Challenge rule: Consistency & Validation

Source: `docs/Coding_Challenge_V2_English.md` → Requirements / Consistency + Validation.

## Invariants (must hold at every moment)

- The balance never changes without a valid operation.
- Debit never results in a negative balance (insufficient funds → operation **not executed**, balance unchanged).
- A transaction is never applied to the balance more than once.
- Transfer has an atomic effect on source and destination; the amount deducted from the source exactly equals the amount added to the destination.

## Required validation

- **Invalid amount** — `amount <= 0` must be rejected.
- **Unknown account** — operating on a non-existent account must fail with an appropriate error.
- **Same-account transfer** — `transfer(A, A, 100, "TX-100")` must have a clearly defined, reasonable behavior (reject, or no-op with balance unchanged). The decision and its reasoning go in the Persian README.

## Failure semantics

- If a transaction's execution fails, the final state must be consistent with the designed semantics — never a partially applied operation.
