package io.github.sudoitir.taraz.core.domain.common;

/**
 * Business rule as a predicate (Fowler) with a {@link Result}-returning edge (ADR-0011).
 *
 * <p>Rules are checked before any mutation, so a violated rule never leaves a half-applied state.
 */
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    /** What to report when the rule is not satisfied. Only meaningful for a violating candidate. */
    DomainError violation(T candidate);

    /** Success carrying the candidate, or failure carrying {@link #violation}. */
    Result<T> check(T candidate);

    /** Fail-fast and left-biased: reports the first violation. */
    Specification<T> and(Specification<T> other);

    Specification<T> or(Specification<T> other);

    /** Negation needs the failure to report explicitly — a negated rule cannot infer a readable error. */
    Specification<T> not(DomainError whenSatisfied);
}
