package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.outbound.GateDecision;
import io.github.sudoitir.taraz.core.application.ports.outbound.IdempotencyGate;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * ADR-0020/0021/0041: a pure fail-open <em>read-through cache</em>, not a reservation gate.
 * {@code CreditHandler}/{@code DebitHandler}/{@code TransferHandler} already treat {@link
 * GateDecision.Won} and {@link GateDecision.Unknown} identically, and ADR-0041 states there is no
 * {@code IN_PROGRESS} state a reader must interpret — so this adapter never writes a placeholder
 * before the atomic unit completes, and {@code Won} is unreachable from it in practice.
 * Implementing ADR-0021's original reservation protocol as the live mechanism would recreate exactly
 * the crash window ADR-0041 exists to eliminate.
 *
 * <p>Every failure path — cache miss, unparseable value, timeout, connection error — degrades to
 * {@link GateDecision.Unknown}. This adapter deliberately catches broadly ({@code RuntimeException}):
 * {@link ProcessedOutcomeCodec} and Lettuce's client can each throw different unchecked exception
 * hierarchies, and the contract here is "any failure ⇒ Unknown", not "these specific failures".
 */
@Component
public final class ValkeyIdempotencyGate implements IdempotencyGate {

    private static final Logger log = LoggerFactory.getLogger(ValkeyIdempotencyGate.class);
    private static final String KEY_PREFIX = "taraz:idem:v1:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ProcessedOutcomeCodec codec;

    public ValkeyIdempotencyGate(StringRedisTemplate redis, ProcessedOutcomeCodec codec) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public GateDecision tryBegin(TransactionId id) {
        try {
            String raw = redis.opsForValue().get(key(id));
            if (raw == null || raw.isBlank()) {
                return new GateDecision.Unknown();
            }
            return new GateDecision.AlreadyApplied(codec.fromJson(id, raw));
        } catch (RuntimeException e) {
            log.debug("idempotency gate unavailable for {} — degrading to Unknown", id, e);
            return new GateDecision.Unknown();
        }
    }

    @Override
    public void publishOutcome(TransactionId id, CommandOutcome outcome) {
        try {
            redis.opsForValue().set(key(id), codec.toJson(outcome), TTL);
        } catch (RuntimeException e) {
            // Best-effort (ADR-0021): PostgreSQL is authoritative, so a failed cache write only
            // costs the next duplicate a slower path, never correctness.
            log.debug("could not cache outcome for {}", id, e);
        }
    }

    @Override
    public void release(TransactionId id) {
        try {
            redis.delete(key(id));
        } catch (RuntimeException e) {
            log.debug("could not release {}", id, e);
        }
    }

    private static String key(TransactionId id) {
        return KEY_PREFIX + id.value();
    }
}
