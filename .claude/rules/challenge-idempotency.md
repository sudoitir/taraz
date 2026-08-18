# Challenge rule: Idempotency

Source: `docs/Coding_Challenge_V2_English.md` → Requirements / Idempotency.

- `transactionId` is the **unique identifier of a financial operation**. Requests may be sent multiple times due to retries.
- `credit(A, 500, "TX-100")` received N times must change the balance **exactly once** (1000 → 1500, never 1000 → 3000).
- This applies equally to **credit, debit, and transfer**.
- Duplicate requests may arrive **concurrently** — idempotency must hold under races, not just sequential retries.
- Re-executing an already-applied `transactionId` must be a safe no-op (or a clearly defined, documented result — never a second balance change).
- The README must explain: how double execution is prevented, and what happens when the same transaction arrives multiple times concurrently.
