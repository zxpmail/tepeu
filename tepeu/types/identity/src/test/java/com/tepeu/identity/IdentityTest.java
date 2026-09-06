package com.tepeu.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentityTest {

    @Test
    void brandedIdsRejectBlank() {
        assertThrows(NullPointerException.class, () -> new PrincipalId(null));
        assertThrows(IllegalArgumentException.class, () -> new PrincipalId(" "));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceId(""));
        assertThrows(IllegalArgumentException.class, () -> new SessionId("\t"));
    }

    @Test
    void brandedIdsEqualByValue() {
        assertEquals(new PrincipalId("p1"), new PrincipalId("p1"));
        assertEquals(new WorkspaceId("ws"), new WorkspaceId("ws"));
        assertEquals(new SessionId("s1"), new SessionId("s1"));
        assertEquals("s1", new SessionId("s1").toString());
    }

    @Test
    void invokeContextCancelIsFlag() {
        InvokeContext ctx = new InvokeContext(
                new Principal(new PrincipalId("p1")),
                new WorkspaceId("ws"),
                new SessionId("s1"));
        assertFalse(ctx.isCancelled());
        ctx.cancel();
        assertTrue(ctx.isCancelled());
        assertEquals("s1", ctx.sessionId().value());
    }
}
