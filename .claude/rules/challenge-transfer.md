# Challenge rule: Transfer atomicity

Source: `docs/Coding_Challenge_V2_English.md` → Requirements / Transfer Atomicity (+ scenario).

- `transfer(source, destination, amount, transactionId)` is **atomic**: no observable state where the amount left the source but hasn't reached the destination, or vice versa.
- Example: `A = 1000`, `B = 500`, `transfer(A, B, 300, "TX-1")` → valid result is only `A = 700, B = 800`. Forbidden states: `A = 700, B = 500` and `A = 1000, B = 800`.
- On failure, the system must end in a state consistent with the designed semantics — never a partial transfer.
- Insufficient source funds → transfer fails entirely, both balances unchanged.
- The mechanism is your choice (e.g. ordered locking, single critical section) — but it must be **deadlock-free by design or have its deadlock conditions explicitly analyzed**.
- The README must answer: how atomicity is guaranteed; is deadlock possible? If yes, under what conditions; if no, how it is prevented (e.g. consistent lock ordering by account ID).
