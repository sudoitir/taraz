package io.github.sudoitir.taraz.adapters.driven.persistence.transaction;

import io.github.sudoitir.taraz.adapters.driven.persistence.common.MoneyEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Flat JPA entity for one double-entry leg (ADR-0015/0037). {@code transactionId} is a plain FK
 * column — no {@code @ManyToOne}, no relation graph (ADR-0015): the parent is persisted as a separate,
 * explicit {@code em.persist} call, never a cascade.
 */
@Entity
@Table(name = "ledger_entry")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntryEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private UUID transactionId;

    @Column(nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, updatable = false, length = 8)
    private String direction;

    @Embedded
    @AttributeOverride(
            name = "minorUnits",
            column = @Column(name = "amount_minor_units", nullable = false, updatable = false))
    private MoneyEmbeddable amount;
}
