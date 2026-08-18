package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.outbound.ProcessedTransactionStore;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeProcessedTransactionStore implements ProcessedTransactionStore {

    private final Map<TransactionId, CommandOutcome> processed = new ConcurrentHashMap<>();

    @Override
    public Optional<CommandOutcome> find(TransactionId id) {
        return Optional.ofNullable(processed.get(id));
    }

    @Override
    public void record(TransactionId id, CommandOutcome outcome) {
        processed.put(id, outcome);
    }

    public int recordedCount() {
        return processed.size();
    }
}
