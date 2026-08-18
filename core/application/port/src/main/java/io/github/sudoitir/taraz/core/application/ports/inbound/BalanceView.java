package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;

/**
 * A read-side snapshot of an account's balance. Carries the domain value objects directly, not
 * primitives: {@link Money} is exact and unbounded (ADR-0036), so flattening it to a {@code long} here
 * would silently reintroduce the overflow the domain spec explicitly rejects. The one place this
 * narrows to {@code long} is {@link BalanceService#getBalance}, at the challenge's mandated boundary.
 */
public record BalanceView(AccountId accountId, Money balance) {}
