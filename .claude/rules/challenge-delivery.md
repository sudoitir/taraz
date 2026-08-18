# Challenge rule: Technology, API contract & delivery

Source: `docs/Coding_Challenge_V2_English.md` → Technology, API, Project Structure, Submission, Most Important, Use of AI.

## Technology

- **Required**: Java 21, Maven (challenge allows Gradle or Maven — this project uses Maven), Spring Boot.
- **Optional** (DB, Redis, Kafka, Docker, other infra): only if it solves a real problem. For each one, the README must state: why it was used, what problem it solves, what trade-offs it introduces. **Never add infrastructure just to look complex** — a simple, well-designed solution is fully acceptable.

## API contract (minimum)

An HTTP API is **not mandatory**. Minimum is this interface (REST exposure optional):

```java
public interface BalanceService {
    void credit(String accountId, long amount, String transactionId);
    void debit(String accountId, long amount, String transactionId);
    void transfer(String sourceAccountId, String destinationAccountId,
                  long amount, String transactionId);
    long getBalance(String accountId);
}
```

Operation semantics: credit increases balance; debit decreases it only with sufficient funds (otherwise fails without executing); transfer moves amount atomically between two accounts.

## Project structure

- Logical separation with at least `src/main/java/` and `src/test/java/`; architecture/pattern choice is free but must be coherent.

## Submission

- Git repository containing: source code, tests, Persian `README.md`, build configuration.
- Buildable and testable via one standard command: `./mvnw test`.
- Any special dependency/configuration needed to build or test is explained in the README.

## Priorities & honesty

- **Correctness, concurrency behavior, idempotency, reliable transfer, and tests for critical scenarios outrank feature count.**
- If time runs out: README lists what is implemented, what remains, and how it would be continued.

## AI-use expectation

- AI assistants are permitted, but every submitted line must be fully understandable and defensible in review: design choices, concurrency management, idempotency guarantee, failure behavior, performance characteristics, trade-offs.
