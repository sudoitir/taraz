package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

    private final UuidV7IdGenerator generator = new UuidV7IdGenerator();

    @Test
    void generatesVersion7Uuids() {
        assertThat(generator.newId().version()).isEqualTo(7);
    }

    @Test
    void successiveIdsAreLexicographicallyOrdered() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.newId());
        }
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i))
                    .as("id %d must sort after its predecessor (JUG increments entropy within a millisecond)", i)
                    .isGreaterThan(ids.get(i - 1));
        }
    }

    @Test
    void idsAreDistinct() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.newId());
        }
        assertThat(ids).doesNotHaveDuplicates();
    }
}
