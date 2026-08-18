# Challenge rule: Concurrency

Source: `docs/Coding_Challenge_V2_English.md` → Requirements / Concurrency.

- Multiple requests may execute concurrently against the **same account**. Example: `balance = 1000`, `Thread 1 → debit(A, 700)`, `Thread 2 → debit(A, 700)` → **exactly one** may succeed.
- Under concurrency the system must **never** produce:
  - Negative balance
  - Lost update
  - Incorrect balance
  - Partial execution of an operation
- Operations on **independent accounts must not block each other** without a valid reason (`Thread 1 → A`, `Thread 2 → B` proceed independently as much as possible). No global lock over unrelated accounts.
- Every operation on a single account is atomic with respect to other operations on that account.
- The chosen mechanism (e.g. per-account locking, striped locks, CAS) must be explained in the Persian README: how thread safety is ensured, why this approach, and what happens to same-account operations under concurrency.
