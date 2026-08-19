package io.github.sudoitir.taraz.container.it;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jspecify.annotations.Nullable;

/**
 * Raw {@link KafkaConsumer} helpers shared by the outbox/correlation IT suite. Every IT class in this
 * suite shares one static Kafka container ({@link AbstractTarazIT}), so by the time a Kafka-consuming
 * test runs, {@code taraz.account.v1} may already carry hundreds/thousands of records from earlier
 * classes (e.g. the 1000-op concurrency suite). Subscribing from {@code earliest} would force each test
 * to drain that whole backlog before ever seeing its own record — slow and, under load, flaky. Instead,
 * {@link #openFromLatest} establishes the subscription's starting offset at "now" *before* the test
 * triggers its own operation, so only genuinely new records are ever seen.
 */
final class TestKafkaConsumers {

    private TestKafkaConsumers() {}

    /**
     * Opens a consumer, subscribes to {@code topic}, and forces partition assignment + an
     * {@code auto.offset.reset=latest} seek to happen now, before returning — so records produced by
     * work the caller triggers immediately afterward are guaranteed to be visible to it.
     */
    static KafkaConsumer<String, byte[]> openFromLatest(String bootstrapServers, String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        // poll(timeout) blocks for the FULL duration whenever nothing is available to fetch yet — which
        // is exactly the steady state right after a "latest" reset (nothing to read, by design). A
        // single long poll would burn that whole timeout every call. Instead, poll in short bursts and
        // stop the moment partition assignment lands — group join + the offset-reset-to-latest seek both
        // resolve synchronously within whichever poll call completes assignment, so nothing is missed.
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (consumer.assignment().isEmpty() && Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(200));
        }
        return consumer;
    }

    /** Polls an already-{@link #openFromLatest opened} consumer, accumulating every record seen over {@code window}. */
    static List<ConsumerRecord<String, byte[]>> drain(KafkaConsumer<String, byte[]> consumer, Duration window) {
        List<ConsumerRecord<String, byte[]>> collected = new ArrayList<>();
        Instant deadline = Instant.now().plus(window);
        while (Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(300)).forEach(collected::add);
        }
        return collected;
    }

    /**
     * Polls an already-{@link #openFromLatest opened} consumer until a record matching {@code target}
     * is seen or {@code timeout} elapses, returning as soon as it's found instead of always burning the
     * whole window — this suite shares one Kafka container/topic across many IT classes (see class
     * javadoc), so under-load publish latency for one specific occurrence is real, not test slop; a
     * generous {@code timeout} tolerates that without slowing down the common case where the record
     * shows up almost immediately.
     */
    static ConsumerRecord<String, byte[]> pollUntilFound(
            KafkaConsumer<String, byte[]> consumer,
            java.util.function.Predicate<ConsumerRecord<String, byte[]>> target,
            Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(300))) {
                if (target.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("no matching Kafka record arrived within " + timeout);
    }

    @Nullable
    static String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
