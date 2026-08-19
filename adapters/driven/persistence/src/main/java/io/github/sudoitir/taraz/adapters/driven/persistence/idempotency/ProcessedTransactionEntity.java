package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Flat JPA entity for {@code processed_transaction} (ADR-0021/0041/0047) — the authoritative
 * idempotency record. PK is the natural key ({@code transactionId}), not a surrogate: this table has
 * exactly one access path and no children (ADR-0047 narrows ADR-0016 for this case). {@code outcome} is
 * jsonb via Hibernate 7's native {@code @JdbcTypeCode(SqlTypes.JSON)} — no custom {@code UserType}
 * needed.
 */
@Entity
@Table(name = "processed_transaction")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTransactionEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 64)
    private String transactionId;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(nullable = false, updatable = false)
    private UUID ledgerTransactionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String outcome;
}
