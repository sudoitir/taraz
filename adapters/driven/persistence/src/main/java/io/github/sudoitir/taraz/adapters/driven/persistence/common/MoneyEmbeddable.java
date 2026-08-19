package io.github.sudoitir.taraz.adapters.driven.persistence.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The one {@code @Embeddable} every amount column reuses (ADR-0015). The column name is supplied by
 * each owning entity via {@code @AttributeOverride}, since {@code account.balance_minor_units} and
 * {@code ledger_entry.amount_minor_units} name the same shape differently.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoneyEmbeddable {

    @Column(nullable = false)
    private BigDecimal minorUnits;
}
