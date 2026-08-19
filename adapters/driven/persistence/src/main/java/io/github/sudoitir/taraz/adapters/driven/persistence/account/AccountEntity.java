package io.github.sudoitir.taraz.adapters.driven.persistence.account;

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
 * Flat JPA entity for the {@code account} table (ADR-0015) — deliberately not the domain
 * {@code Account} aggregate, which has no public setters or no-arg constructor by design. No
 * {@code @Version} column: ADR-0026 supersedes ADR-0017's optimistic locking with ordered pessimistic
 * row locks.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Embedded
    @AttributeOverride(name = "minorUnits", column = @Column(name = "balance_minor_units", nullable = false))
    private MoneyEmbeddable balance;
}
