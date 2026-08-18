package io.github.sudoitir.taraz.core.domain.common;

import java.util.Objects;

/** Fail-fast, left-biased conjunction: reports the first violation, so the caller gets the most specific reason. */
final class AndSpecification<T> extends AbstractSpecification<T> {

    private final Specification<T> left;
    private final Specification<T> right;

    AndSpecification(Specification<T> left, Specification<T> right) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) && right.isSatisfiedBy(candidate);
    }

    @Override
    public DomainError violation(T candidate) {
        return left.isSatisfiedBy(candidate) ? right.violation(candidate) : left.violation(candidate);
    }
}
