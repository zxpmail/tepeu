package com.tepeu.os.loop;

import com.tepeu.os.loop.local.DoomLoop;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoomLoopTest {

    @Test
    void stripsTimestampAndRandomKeys() {
        String a = DoomLoop.fingerprint("echo", Map.of("text", "hi", "timestamp", "1", "random", "x"));
        String b = DoomLoop.fingerprint("echo", Map.of("text", "hi", "timestamp", "2", "random", "y"));
        assertEquals(a, b);
        assertEquals(DoomLoop.fingerprint("echo", Map.of("text", "hi")), a);
    }

    @Test
    void differentToolOrArgIsDifferentFingerprint() {
        assertNotEquals(
                DoomLoop.fingerprint("echo", Map.of("text", "a")),
                DoomLoop.fingerprint("echo", Map.of("text", "b")));
        assertNotEquals(
                DoomLoop.fingerprint("echo", Map.of()),
                DoomLoop.fingerprint("other", Map.of()));
    }

    @Test
    void nudgeIsModelVisiblePayload() {
        String n = DoomLoop.nudge("echo");
        assertTrue(n.startsWith(DoomLoop.ERROR_CODE));
        assertFalse(n.isBlank());
    }
}
