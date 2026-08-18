package io.github.sudoitir.taraz.core.domain.common;

import java.util.Objects;

/** Negation: the error to report when the inner rule holds is supplied explicitly at composition time. */
final class NotSpecification<T> extends AbstractSpecification<T> {

    private final Specification<T> inner;
    private final DomainError whenSatisfied;

    NotSpecification(Specification<T> inner, DomainError whenSatisfied) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.whenSatisfied = Objects.requireNonNull(whenSatisfied, "whenSatisfied");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return !inner.isSatisfiedBy(candidate);
    }

    @Override
    public DomainError violation(T candidate) {
        return whenSatisfied;
    }
}
