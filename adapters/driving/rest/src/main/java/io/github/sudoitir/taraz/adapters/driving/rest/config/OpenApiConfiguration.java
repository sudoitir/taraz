package io.github.sudoitir.taraz.adapters.driving.rest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the generated OpenAPI document (ADR-0059). Paths and schemas are inferred from
 * the controllers; only what inference cannot know lives here.
 */
// proxyBeanMethods=false: no inter-bean calls here, and a full @Configuration may not be final
// (CGLIB proxying) — lite mode keeps the project's `final class` convention.
@Configuration(proxyBeanMethods = false)
public final class OpenApiConfiguration {

    @Bean
    OpenAPI tarazOpenAPI() {
        String version = getClass().getPackage().getImplementationVersion();
        return new OpenAPI()
                .info(new Info()
                        .title("Taraz — Concurrent Balance Service")
                        .description("Correct, consistent, idempotent balance operations (credit / debit / transfer)"
                                + " under high concurrency. Command endpoints require the `Idempotency-Key` header"
                                + " — it IS the transaction id; a replayed key returns the stored outcome instead of"
                                + " re-applying. Errors are RFC 7807 problem details.")
                        .version(version == null ? "dev" : version));
    }
}
