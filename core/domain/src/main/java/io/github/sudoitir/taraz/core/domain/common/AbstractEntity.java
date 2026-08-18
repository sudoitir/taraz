package io.github.sudoitir.taraz.core.domain.common;

import java.util.Objects;

/**
 * Identity-based entity base. {@code equals}/{@code hashCode} are final and {@code getClass()}-based (not
 * {@code instanceof}): entities are mutable and subclassable, and {@code instanceof} equality breaks
 * symmetry the moment a subclass appears. The hash uses only the immutable id, so it never shifts while
 * the entity sits in a collection.
 *
 * <p>Not thread-safe: each command loads a fresh instance under a row lock and discards it at commit
 * (ADR-0026); instances are never shared across threads.
 */
public abstract class AbstractEntity<ID> {

    private final ID id;

    protected AbstractEntity(ID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public final ID id() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEntity<?> other = (AbstractEntity<?>) o;
        return id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
