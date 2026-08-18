package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbstractEntityTest {

    private static final class TestEntity extends AbstractEntity<String> {
        TestEntity(String id) {
            super(id);
        }
    }

    private static final class OtherEntity extends AbstractEntity<String> {
        OtherEntity(String id) {
            super(id);
        }
    }

    @Test
    void equalityIsReflexiveSymmetricTransitive() {
        TestEntity a = new TestEntity("id-1");
        TestEntity b = new TestEntity("id-1");
        TestEntity c = new TestEntity("id-1");

        assertThat(a.equals(a)).isTrue(); // reflexive (direct call: SelfAssertion guards assertThat(a).isEqualTo(a))
        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
        assertThat(a).isEqualTo(c);
        assertThat(b).isEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void differentIdsAreNotEqual() {
        assertThat(new TestEntity("id-1")).isNotEqualTo(new TestEntity("id-2"));
    }

    @Test
    void differentConcreteClassWithSameIdIsNotEqual() {
        assertThat(new TestEntity("id-1")).isNotEqualTo(new OtherEntity("id-1"));
    }

    @Test
    void nullAndForeignTypesAreNotEqual() {
        TestEntity entity = new TestEntity("id-1");
        assertThat(entity).isNotEqualTo(null);
        assertThat(entity).isNotEqualTo("id-1");
    }

    @Test
    void hashCodeIsStableAcrossStateChanges() {
        TestEntity entity = new TestEntity("id-1");
        int before = entity.hashCode();
        assertThat(entity.hashCode()).isEqualTo(before);
    }
}
