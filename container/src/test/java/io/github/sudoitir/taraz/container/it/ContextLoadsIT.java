package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Proves the full Spring context actually boots — every one of the seven outbound ports now has a
 * production bean satisfying it, which no test could exercise before this change (the port interfaces
 * had zero implementations). Valkey/Kafka connection factories are lazily created by Boot, so this
 * only needs a real PostgreSQL (for Liquibase + the JPA repositories) to start successfully.
 */
@TarazIntegrationTest
class ContextLoadsIT extends AbstractTarazIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void theFullApplicationContextBoots() {
        assertThat(context).isNotNull();
        assertThat(context.getBeanNamesForType(
                        io.github.sudoitir.taraz.core.application.ports.inbound.BalanceService.class))
                .isNotEmpty();
    }
}
