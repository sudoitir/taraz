package io.github.sudoitir.taraz.core.domain.transaction.spec;

import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.transaction.TransferParties;

/**
 * A transfer's source and destination must be different accounts (ADR-0028). The single definition of
 * the rule — {@link DistinctTransferAccountsSpecification} adapts a {@code TransactionDraft} to a
 * {@link TransferParties} and delegates here, so a caller can also check the parties alone, before any
 * {@code Transaction} exists.
 */
public final class DistinctTransferPartiesSpecification extends AbstractSpecification<TransferParties> {

    @Override
    public boolean isSatisfiedBy(TransferParties parties) {
        return !parties.source().equals(parties.destination());
    }

    @Override
    public DomainError violation(TransferParties parties) {
        return new DomainError(ErrorCode.SAME_ACCOUNT_TRANSFER, "transfer source and destination must differ");
    }
}
