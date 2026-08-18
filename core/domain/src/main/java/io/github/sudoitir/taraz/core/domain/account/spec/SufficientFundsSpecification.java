package io.github.sudoitir.taraz.core.domain.account.spec;

import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.Objects;

/** An account's balance must cover the amount about to be deducted — a debit never goes negative. */
public final class SufficientFundsSpecification extends AbstractSpecification<Account> {

    private final Money amount;

    public SufficientFundsSpecification(Money amount) {
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    @Override
    public boolean isSatisfiedBy(Account account) {
        return account.balance().isGreaterThanOrEqualTo(amount);
    }

    @Override
    public DomainError violation(Account account) {
        return new DomainError(
                ErrorCode.INSUFFICIENT_FUNDS,
                "balance " + account.balance() + " cannot cover " + amount + " on account " + account.id());
    }
}
