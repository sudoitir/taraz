## Purpose

Publishes committed financial events to Kafka through a durable, at-least-once transactional outbox,
guaranteeing every event and the balance change that caused it appear together or not at all, without
letting the message broker's availability affect a financial write.

## ADDED Requirements

### Requirement: An event is never published without its causing change having committed

An event describing a balance change SHALL become visible to a consumer only after the database
transaction that made that change has committed, never before and never for a change that was rolled
back.

#### Scenario: A rolled-back operation publishes nothing

- **WHEN** an operation fails and its database transaction rolls back
- **THEN** no event describing that attempt is ever published, and no durable record of it exists in
  the outbox

#### Scenario: A committed operation's events become publishable

- **WHEN** a credit, debit, transfer, or account-opening operation commits
- **THEN** every event it recorded is durably stored and eligible for publication, and none of them
  was visible to a consumer before the commit

### Requirement: Every committed event is published at least once, with a stable dedup key

Every event recorded by a committed operation SHALL eventually be delivered to its topic at least
once, carrying a stable identifier a consumer can use to detect redelivery.

#### Scenario: Every committed event eventually appears on its topic

- **WHEN** M operations commit, each recording one or more events
- **THEN** all of those events eventually appear on their topic, each carrying a unique event
  identifier, and no committed event is permanently lost

### Requirement: Events for one account preserve their relative order

Events describing changes to the same account SHALL be delivered to a single consumer in the order
their causing operations committed.

#### Scenario: Sequential operations on one account stay ordered

- **WHEN** an account is credited and then debited in two separate committed operations
- **THEN** a consumer reading that account's events observes the credit before the debit

### Requirement: Broker unavailability does not block or fail a financial operation

The unavailability of the message broker SHALL NOT prevent a credit, debit, or transfer from
completing, and SHALL NOT cause a completed operation to fail.

#### Scenario: Operation succeeds while the broker is unreachable

- **WHEN** a credit, debit, or transfer is submitted while the message broker is unreachable
- **THEN** the operation completes and its balance change is durable, and its events are published
  once the broker becomes reachable again

### Requirement: A request's correlation identifier, when present, travels with its published events

When the request that caused an operation carried a correlation identifier, every event that
operation recorded SHALL carry the same identifier when published.

#### Scenario: Correlation identifier reaches the published event

- **WHEN** a request carrying a correlation identifier causes a committed operation
- **THEN** every event published for that operation carries the same correlation identifier

#### Scenario: Absent correlation identifier is never fabricated

- **WHEN** an operation is caused by a request or process that carried no correlation identifier
- **THEN** its published events carry no correlation identifier, rather than a generated one
