package io.github.sudoitir.taraz.core.domain.account.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.money.Money;

/** An operation amount must be strictly positive (challenge validation rule). */
public final class PositiveAmountSpecification extends AbstractSpecification<Money> {

    @Override
    public boolean isSatisfiedBy(Money amount) {
        return amount.isPositive();
    }

    @Override
    public DomainError violation(Money amount) {
        return new DomainError(ErrorCode.INVALID_AMOUNT, "amount must be positive: " + amount);
    }
}
