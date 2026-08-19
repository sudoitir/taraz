package io.github.sudoitir.taraz.container.it;

/** Container image tags, pinned to match {@code compose.yaml}'s defaults exactly. */
final class TestImages {

    static final String POSTGRES = "postgres:18";
    static final String VALKEY = "valkey/valkey:9";
    static final String KAFKA = "apache/kafka:4.3.1";

    private TestImages() {}
}
