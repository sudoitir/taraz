package io.github.sudoitir.taraz.container.it;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * ADR-0053: {@code @Testcontainers(disabledWithoutDocker = true)} lets {@code ./mvnw test} skip
 * cleanly without Docker on a reviewer's machine — but a skip is indistinguishable from "never ran" in
 * CI, where the concurrency proof the challenge grades must not be silently absent. When the
 * {@code taraz.require.docker} system property is {@code true} (set by the {@code ci} Maven profile)
 * and Docker is not available, this condition throws rather than reporting disabled, so the build
 * fails loudly instead of passing green with the evidence missing.
 */
final class RequireDockerWhenEnforced implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        boolean enforced = Boolean.getBoolean("taraz.require.docker");
        if (enforced && !DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException("Docker is required (-Dtaraz.require.docker=true) but is not available — "
                    + "the Testcontainers-backed concurrency/idempotency proof cannot be skipped in CI");
        }
        return ConditionEvaluationResult.enabled("docker requirement check passed");
    }
}
