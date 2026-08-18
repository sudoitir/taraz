package io.github.sudoitir.taraz.core.domain.transaction.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.LedgerEntry;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionDraft;

/**
 * All legs of a transaction share one amount magnitude — so a transfer's debit and credit are exactly
 * equal and the transaction nets to zero (ADR-0037).
 */
public final class UniformAmountSpecification extends AbstractSpecification<TransactionDraft> {

    @Override
    public boolean isSatisfiedBy(TransactionDraft draft) {
        if (draft.entries().isEmpty()) {
            return false;
        }
        Money first = draft.entries().get(0).amount();
        return draft.entries().stream().allMatch(e -> e.amount().equals(first));
    }

    @Override
    public DomainError violation(TransactionDraft draft) {
        return new DomainError(
                ErrorCode.UNBALANCED_TRANSACTION,
                "all legs of a transaction must share one amount: "
                        + draft.entries().stream().map(LedgerEntry::amount).toList());
    }
}
