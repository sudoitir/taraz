package io.github.sudoitir.taraz.core.domain.common;

/**
 * Base class providing {@link #check} and the combinators. Composites are package-private: they are
 * reachable only through {@code and}/{@code or}/{@code not}, so the set of specification shapes stays
 * closed and testable.
 */
public abstract class AbstractSpecification<T> implements Specification<T> {

    @Override
    public final Result<T> check(T candidate) {
        return isSatisfiedBy(candidate) ? Result.success(candidate) : Result.failure(violation(candidate));
    }

    @Override
    public final Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    @Override
    public final Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    @Override
    public final Specification<T> not(DomainError whenSatisfied) {
        return new NotSpecification<>(this, whenSatisfied);
    }
}
