# Coding Challenge — Concurrent Balance Service

## Senior Java Developer

**Suggested Time**  
4 to 6 hours

## Goal

In this Challenge, you are expected to design and implement a simple service for managing users' account balances.

Your service must be able to correctly, consistently, and reliably manage account balances when a large number of requests are received concurrently.

The main focus of the Challenge is on:

- Correctness
- Concurrent processing
- Data consistency
- Idempotency
- Design quality
- Testability
- Engineering decisions and trade-offs

The number of features you implement is not as important as the quality of your solution and technical decisions.

## Scenario

Assume we have a financial system that maintains an Account with a specific balance for each user.

The service must support the following operations.

### 1. Credit

Increase the balance of an account:

```text
credit(accountId, amount, transactionId)
```

Example:

```text
Initial Balance: 1,000
Credit: 500
-----------------------
Final Balance: 1,500
```

### 2. Debit

Decrease the balance of an account if sufficient balance is available:

```text
debit(accountId, amount, transactionId)
```

If the account balance is insufficient to perform the operation, the operation must not be executed.

Example:

```text
Initial Balance: 1,000
Debit: 700
-----------------------
Final Balance: 300
```

However:

```text
Initial Balance: 1,000
Debit: 1,200
-----------------------
Operation: Failed
Final Balance: 1,000
```

### 3. Transfer

Transfer an amount from one account to another:

```text
transfer(
    sourceAccountId,
    destinationAccountId,
    amount,
    transactionId
)
```

Example:

```text
Account A: 1,000
Account B: 500
Transfer 300 from A to B
Account A: 700
Account B: 800
```

The transfer must be **Atomic**.

This means there must never be a state in which the amount has been deducted from the source account but has not been added to the destination account.

## API

Implementing an HTTP API is not mandatory.

At minimum, the project must contain an interface similar to the following:

```java
public interface BalanceService {
    void credit(
        String accountId,
        long amount,
        String transactionId
    );

    void debit(
        String accountId,
        long amount,
        String transactionId
    );

    void transfer(
        String sourceAccountId,
        String destinationAccountId,
        long amount,
        String transactionId
    );

    long getBalance(String accountId);
}
```

If desired, you may also expose this interface as a REST API.

## Requirements

### 1. Concurrency

Multiple requests may be executed concurrently against the same Account.

For example:

```text
Initial Balance = 1,000
Thread 1 → debit(A, 700)
Thread 2 → debit(A, 700)
```

Under these circumstances, only one of the operations should succeed.

The system must not result in any of the following under concurrent conditions:

- Negative Balance
- Lost Update
- Incorrect Balance
- Partial execution of an operation

Operations on independent accounts should also not block each other without a valid reason.

For example:

```text
Thread 1 → Account A
Thread 2 → Account B
```

They should be processed independently as much as possible.

### 2. Idempotency

A request may be sent multiple times due to a Retry.

`transactionId` is the unique identifier of a financial operation.

For example:

```text
credit(A, 500, "TX-100")
credit(A, 500, "TX-100")
credit(A, 500, "TX-100")
```

All three requests belong to the same Transaction and must not result in a 1,500-unit increase in the balance.

Ultimately, the Transaction's effect on the balance must be applied only once:

```text
Initial Balance = 1,000
Final Balance = 1,500
```

The same behavior must also apply to:

- Credit
- Debit
- Transfer

Important: duplicate requests may be received concurrently.

### 3. Consistency

At every moment, the system's main Invariants must hold.

These include:

- The account balance must not change without a valid operation.
- Debit must not result in a Negative Balance.
- A Transaction must not be applied to the balance more than once.
- Transfer must have an atomic effect on both the source and destination accounts.
- The amount deducted from the source and the amount added to the destination must be exactly equal.

### 4. Validation

At minimum, the following cases must be validated.

#### Invalid Amount

The Transaction amount must be valid.

For example:

```text
amount <= 0
```

must be rejected.

#### Unknown Account

If an Account does not exist, the operation must fail with an appropriate error.

#### Same Account Transfer

The system's behavior in the following condition must be clearly defined and reasonable:

```text
transfer(
    "A",
    "A",
    100,
    "TX-100"
)
```

Explain your decision in the README.

### 5. Transfer Atomicity

Assume:

```text
Account A = 1,000
Account B = 500
```

and the following request is executed:

```text
transfer(A, B, 300, "TX-1")
```

Valid result:

```text
A = 700
B = 800
```

However, the system must never, under normal circumstances, reach a state such as:

```text
A = 700
B = 500
```

or:

```text
A = 1,000
B = 800
```

Also, if execution of the Transaction fails, the final state of the system must be consistent with your designed semantics and must not result in a partial Transfer.

The way you achieve this behavior is up to you.

## Testing

Tests are an important part of the Challenge.

### Idempotency Tests

For example:

```text
Initial Balance = 1,000
Credit 100 with TX-1
Credit 100 with TX-1
Credit 100 with TX-1
```

Expected:

```text
Final Balance = 1,100
```

Also test Idempotency for Debit and Transfer.

### Concurrent Tests

Design at least one Concurrent scenario for a single Account and one scenario involving multiple Accounts.

For example:

```text
Initial Balance = 100,000
1,000 concurrent operations
Expected Final Balance = ...
```

The test must be able to demonstrate that your solution remains Correct under Concurrent conditions.

If possible, design a test that increases the probability of a Race Condition occurring.

## Technology

The implementation must use:

- Java 21
- Gradle or Maven
- Spring Boot

The following are also optional:

- Database
- Redis
- Kafka
- Docker
- Other Infrastructure

If you use any of these technologies, explain in the README:

1. Why you used it.
2. What problem it solves.
3. What Trade-offs it introduces.

Do not use additional Infrastructure simply to make the project more complex.

A simple and well-designed solution is completely acceptable.

## Project Structure

The project structure and code organization are up to you.

At minimum, the code is expected to contain logical sections such as:

```text
src/
├── main/
│   └── java/
│
└── test/
    └── java/
```

Using a specific Architecture or Design Pattern is not mandatory.

## README

Provide a `README.md` file with the project.

The README should briefly explain:

### Architecture

What is the overall structure of your solution?

What are the main components of the system?

### Concurrency

How have you ensured Thread Safety?

Why did you choose this approach?

What happens to operations on the same Account under Concurrent conditions?

### Idempotency

How do you prevent a Transaction from being executed multiple times?

What happens if the same Transaction is received multiple times concurrently?

### Transfer

How do you guarantee Transfer Atomicity?

Is there a possibility of Deadlock in your design?

If yes, under what conditions can it occur?

If no, how have you prevented it?

## Submission

Submit the project as a Git Repository.

The Repository must contain:

- Source Code
- Tests
- `README.md`
- Build Configuration

The project must be buildable and testable using a standard command.

For example:

```text
./gradlew test
```

or:

```text
./mvnw test
```

If any specific Dependency or Configuration is required to run the project or tests, explain it in the README.

## Most Important

This Challenge is designed to evaluate your problem-solving and engineering decision-making abilities.

You do not need to implement everything completely.

If you have to choose between adding more Features and improving Correctness, Concurrency, Testing, or Design, solution quality takes priority.

We will pay particular attention to the following:

- Is your solution Correct?
- Does it behave predictably under Concurrent conditions?
- Is Idempotency implemented correctly?
- Is Transfer performed reliably?
- Have you written appropriate tests for critical scenarios?
- Can you explain your technical decisions?
- Do you understand the limitations and Trade-offs of your solution?

## Use of AI

The use of Coding AI Assistants during the Challenge is permitted.

However, the code you submit must be fully understandable to you.

During the Technical Review, you may be asked about:

- The reason for your Design choices
- How you manage Concurrency
- How you guarantee Idempotency
- System behavior under Failure conditions
- Performance Characteristics
- Trade-offs of your solution

Therefore, you are expected to be able to explain and defend your code and technical decisions.

## Suggested Time

**4 to 6 hours**

If, due to time constraints, you were unable to implement all parts, that is not a problem.

In this case, explain in the README:

- Which parts you implemented.
- Which parts remain.
- If you had more time, how you would continue.

**Good engineering decisions are more important than completing every possible feature.**
