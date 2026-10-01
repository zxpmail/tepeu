package com.tepeu.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendsTest {

    @Test
    void unknownKindIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backends.create("nope", null, null, null));
    }

    @Test
    void anthropicWithoutTokenNamesTheEnvVar() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Backends.create("anthropic", " ", "https://x", "m"));
        assertTrue(e.getMessage().contains("ANTHROPIC_AUTH_TOKEN"));
    }
}
