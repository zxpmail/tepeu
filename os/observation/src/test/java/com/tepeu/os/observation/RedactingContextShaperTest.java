package com.tepeu.os.observation;

import com.tepeu.os.observation.local.RedactingContextShaper;
import com.tepeu.os.observation.local.ContextShapers;
import com.tepeu.os.session.SessionEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactingContextShaperTest {

    @Test
    void redactsSecrets() {
        String out = RedactingContextShaper.redactSecrets("key=sk-abcdefghijklmnopqrstuvwxyz and Bearer deadbeef.token");
        assertEquals("key=[REDACTED] and Bearer [REDACTED]", out);
    }

    @Test
    void truncatesLongToolResults() {
        ContextShaper shaper = ContextShapers.redacting(128);
        String longBody = "x".repeat(200);
        CanonicalTurn turn = new CanonicalTurn(
                CanonicalRole.TOOL,
                SessionEventType.TOOL_RESULT,
                longBody,
                Map.of());
        List<CanonicalTurn> shaped = shaper.shape(List.of(turn));
        assertEquals(1, shaped.size());
        assertTrue(shaped.get(0).body().endsWith("...[truncated]"));
        assertTrue(shaped.get(0).body().length() < longBody.length());
    }

    @Test
    void leavesUserMessagesUntouchedExceptSecrets() {
        ContextShaper shaper = ContextShapers.defaults();
        CanonicalTurn turn = new CanonicalTurn(
                CanonicalRole.USER,
                SessionEventType.USER_MESSAGE,
                "hello",
                Map.of());
        assertEquals(turn, shaper.shape(List.of(turn)).get(0));
    }
}
