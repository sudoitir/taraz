package io.github.sudoitir.taraz.core.application.ports;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;

/** An account's balance as of a command's outcome. */
public record AccountBalance(AccountId accountId, Money balance) {}
