package io.github.sudoitir.taraz.core.application.ports.inbound;

/**
 * The challenge's mandated minimum contract, verbatim. This is a thin facade over
 * {@link CreditUseCase}/{@link DebitUseCase}/{@link TransferUseCase}/{@link GetBalanceUseCase} — those
 * per-use-case ports are CQRS's real contracts (ADR-0007); this interface exists only to satisfy the
 * challenge's literal API and is not how driving adapters read balances (ADR-0033: queries never pass
 * through the write-side service).
 *
 * <p>A predicted domain failure surfaces here as a thrown {@link BalanceOperationException} — the one
 * place in this codebase a {@code Result} failure becomes an exception, forced by these {@code void}/
 * {@code long} signatures.
 */
public interface BalanceService {

    void credit(String accountId, long amount, String transactionId);

    void debit(String accountId, long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId);

    long getBalance(String accountId);
}
