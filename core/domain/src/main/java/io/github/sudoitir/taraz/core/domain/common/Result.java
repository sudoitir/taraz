package io.github.sudoitir.taraz.core.domain.common;

import java.util.Optional;
import java.util.function.Function;

/**
 * Result channel for predicted domain failures (ADR-0005, ADR-0011): {@link Success} carries the value,
 * {@link Failure} carries a {@link DomainError}. Predicted failures are never thrown — exceptions are
 * reserved for programmer errors.
 */
public sealed interface Result<T> {

    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public <R> Result<R> map(Function<? super T, ? extends R> fn) {
            return new Success<>(fn.apply(value));
        }

        @Override
        public <R> Result<R> flatMap(Function<? super T, Result<R>> fn) {
            return fn.apply(value);
        }

        @Override
        public T orElseThrow() {
            return value;
        }

        @Override
        public Optional<DomainError> error() {
            return Optional.empty();
        }
    }

    record Failure<T>(DomainError domainError) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Result<R> map(Function<? super T, ? extends R> fn) {
            return (Result<R>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Result<R> flatMap(Function<? super T, Result<R>> fn) {
            return (Result<R>) this;
        }

        @Override
        public T orElseThrow() {
            throw new IllegalStateException(
                    "orElseThrow on failure: " + domainError.code() + " — " + domainError.message());
        }

        @Override
        public Optional<DomainError> error() {
            return Optional.of(domainError);
        }
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(DomainError error) {
        return new Failure<>(error);
    }

    static <T> Result<T> failure(ErrorCode code, String message) {
        return new Failure<>(new DomainError(code, message));
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    <R> Result<R> map(Function<? super T, ? extends R> fn);

    <R> Result<R> flatMap(Function<? super T, Result<R>> fn);

    /** Tests and programmer assertions only — throws on failure. */
    T orElseThrow();

    Optional<DomainError> error();
}
