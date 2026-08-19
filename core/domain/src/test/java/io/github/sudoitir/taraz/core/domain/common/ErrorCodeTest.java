package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-0048: the catalog carries the two persistence-boundary conflict codes. */
class ErrorCodeTest {

    @Test
    void catalogIncludesTransactionIdConflict() {
        assertThat(ErrorCode.values()).contains(ErrorCode.TRANSACTION_ID_CONFLICT);
    }

    @Test
    void catalogIncludesConcurrencyConflict() {
        assertThat(ErrorCode.values()).contains(ErrorCode.CONCURRENCY_CONFLICT);
    }
}
