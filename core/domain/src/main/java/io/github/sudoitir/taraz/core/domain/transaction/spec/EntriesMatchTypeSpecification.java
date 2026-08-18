package io.github.sudoitir.taraz.core.domain.transaction.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.transaction.EntryDirection;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionDraft;

/**
 * Leg shape must match the transaction type (ADR-0037): CREDIT → exactly one credit leg, DEBIT → exactly
 * one debit leg, TRANSFER → exactly one debit leg and one credit leg.
 */
public final class EntriesMatchTypeSpecification extends AbstractSpecification<TransactionDraft> {

    @Override
    public boolean isSatisfiedBy(TransactionDraft draft) {
        return switch (draft.type()) {
            case CREDIT -> draft.entries().size() == 1 && draft.entries().get(0).direction() == EntryDirection.CREDIT;
            case DEBIT -> draft.entries().size() == 1 && draft.entries().get(0).direction() == EntryDirection.DEBIT;
            case TRANSFER ->
                draft.entries().size() == 2
                        && draft.entries().stream()
                                        .filter(e -> e.direction() == EntryDirection.DEBIT)
                                        .count()
                                == 1
                        && draft.entries().stream()
                                        .filter(e -> e.direction() == EntryDirection.CREDIT)
                                        .count()
                                == 1;
        };
    }

    @Override
    public DomainError violation(TransactionDraft draft) {
        return new DomainError(
                ErrorCode.INVALID_ENTRY_SHAPE,
                draft.type() + " requires a different leg shape than "
                        + draft.entries().size() + " given legs");
    }
}
