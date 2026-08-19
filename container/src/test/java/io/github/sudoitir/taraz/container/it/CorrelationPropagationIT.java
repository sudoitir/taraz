package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.adapters.driving.rest.web.RestHeaders;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * ADR-0052/0056: MDC → outbox row {@code correlation_id} column → Kafka header {@code kafka_correlationId}
 * (Spring Kafka's own convention, not the HTTP header's name). Present correlation id must reach both;
 * absent correlation id must leave both null/omitted — never synthesized.
 */
@TarazIntegrationTest
class CorrelationPropagationIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Test
    void presentCorrelationIdReachesTheOutboxRowAndTheKafkaHeader() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-CORR-PRESENT-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        ConsumerRecord<String, byte[]> record;
        try (KafkaConsumer<String, byte[]> consumer =
                TestKafkaConsumers.openFromLatest(KAFKA.getBootstrapServers(), "taraz.account.v1")) {
            MDC.put(RestHeaders.CORRELATION_ID_MDC_KEY, correlationId);
            try {
                credit.handle(new CreditCommand(account.toString(), 100, txId)).orElseThrow();
            } finally {
                MDC.remove(RestHeaders.CORRELATION_ID_MDC_KEY);
            }
            String eventId = outboxEventIdFor(txId);
            record = TestKafkaConsumers.pollUntilFound(
                    consumer, r -> eventId.equals(TestKafkaConsumers.header(r, "X-Event-Id")), Duration.ofSeconds(60));
        }

        assertThat(outboxCorrelationIdFor(txId)).isEqualTo(correlationId);
        assertThat(TestKafkaConsumers.header(record, KafkaHeaders.CORRELATION_ID))
                .isEqualTo(correlationId);
    }

    @Test
    void absentCorrelationIdLeavesTheOutboxRowAndTheKafkaHeaderEmpty() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-CORR-ABSENT-" + UUID.randomUUID();

        ConsumerRecord<String, byte[]> record;
        try (KafkaConsumer<String, byte[]> consumer =
                TestKafkaConsumers.openFromLatest(KAFKA.getBootstrapServers(), "taraz.account.v1")) {
            MDC.remove(RestHeaders.CORRELATION_ID_MDC_KEY);
            credit.handle(new CreditCommand(account.toString(), 100, txId)).orElseThrow();
            String eventId = outboxEventIdFor(txId);
            record = TestKafkaConsumers.pollUntilFound(
                    consumer, r -> eventId.equals(TestKafkaConsumers.header(r, "X-Event-Id")), Duration.ofSeconds(60));
        }

        assertThat(outboxCorrelationIdFor(txId)).isNull();
        assertThat(TestKafkaConsumers.header(record, KafkaHeaders.CORRELATION_ID))
                .as("no correlation id was ever bound — the header must be omitted, never synthesized")
                .isNull();
    }

    private static String outboxCorrelationIdFor(String transactionId) throws Exception {
        return queryOutboxColumn(transactionId, "correlation_id");
    }

    private static String outboxEventIdFor(String transactionId) throws Exception {
        return queryOutboxColumn(transactionId, "id");
    }

    private static String queryOutboxColumn(String transactionId, String column) throws Exception {
        try (Connection conn = POSTGRES.createConnection("");
                PreparedStatement ps =
                        conn.prepareStatement("SELECT " + column + " FROM outbox WHERE transaction_id = ?")) {
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
