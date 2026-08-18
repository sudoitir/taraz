package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.outbound.TransactionRepository;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeTransactionRepository implements TransactionRepository {

    private final Map<TransactionId, Transaction> saved = new ConcurrentHashMap<>();

    @Override
    public void save(Transaction transaction) {
        saved.put(transaction.id(), transaction);
    }

    public Optional<Transaction> find(TransactionId id) {
        return Optional.ofNullable(saved.get(id));
    }

    public int savedCount() {
        return saved.size();
    }
}
