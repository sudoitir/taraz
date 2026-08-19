## ADDED Requirements

### Requirement: A transaction id reused with different parameters is rejected explicitly

Reusing a `transactionId` that was already applied, but with different operation parameters (a
different account, amount, or operation type than the original application), SHALL fail with a
distinct, stable error code rather than being replayed as though it were an identical duplicate and
rather than surfacing as an unclassified failure.

#### Scenario: Same transaction id, different amount

- **WHEN** a `transactionId` already applied to a credit of 100 is resubmitted as a credit of 200 on
  the same account
- **THEN** the second request fails with a distinct error code identifying the conflict, and the
  affected account's balance reflects only the original application

#### Scenario: Same transaction id, different account

- **WHEN** a `transactionId` already applied to an operation on account A is resubmitted as an
  otherwise-identical operation on account B
- **THEN** the second request fails with the same distinct conflict error code, and neither account's
  balance changes as a result of the second request
