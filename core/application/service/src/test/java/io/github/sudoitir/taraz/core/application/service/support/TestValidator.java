package io.github.sudoitir.taraz.core.application.service.support;

import jakarta.validation.Validation;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * A real {@code jakarta.validation.Validator} (hibernate-validator, test-scope) backing
 * {@link CommandValidator} in tests. Uses {@link ParameterMessageInterpolator} instead of the default
 * resource-bundle interpolator so no {@code jakarta.el} implementation is needed on the test classpath —
 * this project asserts on {@link io.github.sudoitir.taraz.core.domain.common.ErrorCode}, never on
 * interpolated message text.
 */
public final class TestValidator {

    private TestValidator() {}

    public static CommandValidator commandValidator() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(
                        org.hibernate.validator.HibernateValidator.class)
                .configure();
        return new CommandValidator(configuration
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()
                .getValidator());
    }
}
