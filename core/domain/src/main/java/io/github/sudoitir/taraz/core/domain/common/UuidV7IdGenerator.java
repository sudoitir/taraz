package io.github.sudoitir.taraz.core.domain.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.UUID;

/**
 * JUG-backed UUIDv7 generator — the only class in the domain that imports the library (ADR-0038).
 *
 * <p>The JUG generator is documented thread-safe, so a single instance is shared. Its internal
 * synchronization is a nanosecond-scale in-memory critical section (no blocking call inside), so it is
 * not the virtual-thread pinning hazard ADR-0002 warns about.
 */
public final class UuidV7IdGenerator implements IdGenerator {

    private final NoArgGenerator generator = Generators.timeBasedEpochGenerator();

    @Override
    public UUID newId() {
        return generator.generate();
    }
}
