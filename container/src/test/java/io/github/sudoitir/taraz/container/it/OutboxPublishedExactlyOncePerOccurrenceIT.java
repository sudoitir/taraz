package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0010/0027/0055: the outbox delivers at-least-once by construction (send-then-mark) — this proves
 * that for one committed occurrence, exactly one Kafka record carrying its {@code X-Event-Id} lands on
 * the aggregate's topic (ADR-0051's {@code taraz.account.v1}), keyed by the aggregate id, under normal
 * operation. A real consumer, not a mock, so the wire bytes stored at append time (ADR-0050) are proven
 * to actually deserialize and route correctly end to end.
 */
@TarazIntegrationTest
class OutboxPublishedExactlyOncePerOccurrenceIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Test
    void oneCommittedCreditPublishesExactlyOneKafkaRecordForItsEventId() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-OUTBOX-" + UUID.randomUUID();

        List<ConsumerRecord<String, byte[]>> records;
        try (KafkaConsumer<String, byte[]> consumer =
                TestKafkaConsumers.openFromLatest(KAFKA.getBootstrapServers(), "taraz.account.v1")) {
            credit.handle(new CreditCommand(account.toString(), 250, txId)).orElseThrow();
            records = TestKafkaConsumers.drain(consumer, Duration.ofSeconds(30));
        }

        String eventId = outboxRowIdFor(txId);

        List<ConsumerRecord<String, byte[]>> matching = records.stream()
                .filter(r -> eventId.equals(TestKafkaConsumers.header(r, "X-Event-Id")))
                .toList();

        assertThat(matching)
                .as("exactly one Kafka record for this occurrence's event id, never zero or duplicated")
                .hasSize(1);
        assertThat(matching.get(0).key())
                .as("partition key is the aggregate id, so per-account ordering holds (ADR-0051)")
                .isEqualTo(account.toString());
    }

    private static String outboxRowIdFor(String transactionId) throws Exception {
        try (Connection conn = POSTGRES.createConnection("");
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM outbox WHERE transaction_id = ?")) {
            ps.setString(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("outbox row must exist for this transaction")
                        .isTrue();
                return rs.getString(1);
            }
        }
    }
}
