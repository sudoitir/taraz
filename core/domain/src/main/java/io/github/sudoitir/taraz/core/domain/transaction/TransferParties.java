package io.github.sudoitir.taraz.core.domain.transaction;

import io.github.sudoitir.taraz.core.domain.account.AccountId;

/**
 * A transfer's source and destination, checkable before any {@link Transaction} is built, any lock is
 * acquired, or any amount is validated (ADR-0028).
 */
public record TransferParties(AccountId source, AccountId destination) {}
