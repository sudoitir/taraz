package io.github.sudoitir.taraz.core.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdTest {

    @Test
    void blankOrNullIdentifierRejected() {
        assertThat(AccountId.of("  ").error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ACCOUNT_ID));
        assertThat(AccountId.of(null).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ACCOUNT_ID));
    }

    @Test
    void malformedIdentifierRejected() {
        assertThat(AccountId.of("not-a-uuid").error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ACCOUNT_ID));
    }

    @Test
    void wellFormedIdentifierAccepted() {
        UUID uuid = UUID.randomUUID();
        assertThat(AccountId.of(uuid.toString()).orElseThrow()).isEqualTo(new AccountId(uuid));
    }

    @Test
    void orderingUsesUnsignedComparisonNotJdkSignedComparison() {
        // most-significant-bits: -1L (all ones) vs Long.MAX_VALUE (0x7FFF...FFFF, high bit 0)
        AccountId highBitSet = new AccountId(new UUID(-1L, 0L));
        AccountId highBitClear = new AccountId(new UUID(Long.MAX_VALUE, 0L));

        // JDK's UUID.compareTo treats the halves as signed longs: -1L < Long.MAX_VALUE
        assertThat(highBitSet.value().compareTo(highBitClear.value())).isLessThan(0);

        // PostgreSQL / unsigned byte ordering treats -1L's all-ones bit pattern as the larger value —
        // AccountId must agree with this, not with UUID.compareTo above.
        assertThat(highBitSet.compareTo(highBitClear)).isGreaterThan(0);
    }

    @Test
    void orderingIsReflexiveAntisymmetricAndTransitive() {
        AccountId a = new AccountId(new UUID(0L, 1L));
        AccountId aCopy = new AccountId(new UUID(0L, 1L));
        AccountId b = new AccountId(new UUID(0L, 2L));
        AccountId c = new AccountId(new UUID(0L, 3L));

        assertThat(a.compareTo(aCopy)).isZero();
        assertThat(a.compareTo(b)).isLessThan(0);
        assertThat(b.compareTo(a)).isGreaterThan(0);
        assertThat(a.compareTo(c)).isLessThan(0);
    }
}
