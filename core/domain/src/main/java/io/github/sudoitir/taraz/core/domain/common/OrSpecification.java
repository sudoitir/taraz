package io.github.sudoitir.taraz.core.domain.common;

import java.util.Objects;

/** Disjunction: satisfied when either side holds; when neither holds, reports the left violation. */
final class OrSpecification<T> extends AbstractSpecification<T> {

    private final Specification<T> left;
    private final Specification<T> right;

    OrSpecification(Specification<T> left, Specification<T> right) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) || right.isSatisfiedBy(candidate);
    }

    @Override
    public DomainError violation(T candidate) {
        // Only called when neither side holds; the left reason is the deterministic report.
        return left.violation(candidate);
    }
}
