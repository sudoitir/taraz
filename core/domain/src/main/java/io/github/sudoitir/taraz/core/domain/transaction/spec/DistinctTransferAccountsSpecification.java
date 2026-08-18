package io.github.sudoitir.taraz.core.domain.transaction.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionDraft;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionType;
import io.github.sudoitir.taraz.core.domain.transaction.TransferParties;

/**
 * A transfer's source and destination must be different accounts (ADR-0028). Adapts the draft's legs to
 * {@link TransferParties} and delegates the actual rule to {@link DistinctTransferPartiesSpecification} —
 * one definition, two call sites (this one during {@code Transaction} construction, the other standalone
 * before any lock or transaction is opened).
 */
public final class DistinctTransferAccountsSpecification extends AbstractSpecification<TransactionDraft> {

    private static final DistinctTransferPartiesSpecification PARTIES = new DistinctTransferPartiesSpecification();

    @Override
    public boolean isSatisfiedBy(TransactionDraft draft) {
        if (draft.type() != TransactionType.TRANSFER || draft.entries().size() != 2) {
            return true; // shape problems are reported by EntriesMatchTypeSpecification, not here
        }
        return PARTIES.isSatisfiedBy(partiesOf(draft));
    }

    @Override
    public DomainError violation(TransactionDraft draft) {
        return PARTIES.violation(partiesOf(draft));
    }

    private static TransferParties partiesOf(TransactionDraft draft) {
        return new TransferParties(
                draft.entries().get(0).accountId(), draft.entries().get(1).accountId());
    }
}
