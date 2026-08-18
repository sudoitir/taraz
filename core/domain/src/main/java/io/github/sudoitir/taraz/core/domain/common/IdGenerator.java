package io.github.sudoitir.taraz.core.domain.common;

import java.util.UUID;

/**
 * Domain-owned identity source (ADR-0005: external need = local interface). The domain never imports the
 * generator library outside the single implementation of this interface (ADR-0038).
 */
public interface IdGenerator {

    /** A new UUIDv7 (RFC 9562) identifier — time-ordered, suitable for database indexes (ADR-0016). */
    UUID newId();
}
