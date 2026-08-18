package io.github.sudoitir.taraz.core.domain.transaction.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionDraft;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionType;

/** A transfer's source and destination must be different accounts (ADR-0028). */
public final class DistinctTransferAccountsSpecification extends AbstractSpecification<TransactionDraft> {

    @Override
    public boolean isSatisfiedBy(TransactionDraft draft) {
        if (draft.type() != TransactionType.TRANSFER || draft.entries().size() != 2) {
            return true; // shape problems are reported by EntriesMatchTypeSpecification, not here
        }
        return !draft.entries().get(0).accountId().equals(draft.entries().get(1).accountId());
    }

    @Override
    public DomainError violation(TransactionDraft draft) {
        return new DomainError(ErrorCode.SAME_ACCOUNT_TRANSFER, "transfer source and destination must differ");
    }
}
