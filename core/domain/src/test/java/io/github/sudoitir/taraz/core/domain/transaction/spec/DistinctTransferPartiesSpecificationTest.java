package io.github.sudoitir.taraz.core.domain.transaction.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.transaction.TransferParties;
import org.junit.jupiter.api.Test;

class DistinctTransferPartiesSpecificationTest {

    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();
    private static final DistinctTransferPartiesSpecification SPEC = new DistinctTransferPartiesSpecification();

    private static AccountId account() {
        return new AccountId(IDS.newId());
    }

    @Test
    void distinctAccountsSatisfyTheSpecification() {
        var result = SPEC.check(new TransferParties(account(), account()));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void sameAccountFailsWithoutBuildingAnyTransaction() {
        AccountId a = account();

        var result = SPEC.check(new TransferParties(a, a));

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
    }
}
