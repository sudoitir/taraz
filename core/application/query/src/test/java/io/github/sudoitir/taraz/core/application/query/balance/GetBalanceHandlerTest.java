package io.github.sudoitir.taraz.core.application.query.balance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.outbound.AccountBalanceReadRepository;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link GetBalanceHandler}'s constructor takes only {@link AccountBalanceReadRepository} — there is no
 * write-side port to pass it even if a test wanted to; "never mutates, opens no transaction, takes no
 * lock" is a structural fact here, not a behavior a mock needs to verify.
 */
class GetBalanceHandlerTest {

    private static final IdGenerator IDS = new UuidV7IdGenerator();

    private final Map<AccountId, BalanceView> views = new HashMap<>();
    private final GetBalanceHandler handler = new GetBalanceHandler(id -> Optional.ofNullable(views.get(id)));

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    @Test
    void returnsBalanceForExistingAccount() {
        AccountId id = newAccountId();
        views.put(id, new BalanceView(id, Money.of(500).orElseThrow()));

        var result = handler.handle(new GetBalanceQuery(id.toString()));

        assertThat(result.orElseThrow().balance()).isEqualTo(Money.of(500).orElseThrow());
    }

    @Test
    void unknownAccountFailsWithoutCreatingAnything() {
        var result = handler.handle(new GetBalanceQuery(newAccountId().toString()));

        assertThat(result.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
        assertThat(views).isEmpty();
    }

    @Test
    void malformedAccountIdFailsBeforeAnyLookup() {
        var result = handler.handle(new GetBalanceQuery("not-a-uuid"));

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ACCOUNT_ID));
    }
}
