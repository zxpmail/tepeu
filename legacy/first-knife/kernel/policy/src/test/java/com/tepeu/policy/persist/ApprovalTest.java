package com.tepeu.policy.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.policy.ApprovalRecord;
import com.tepeu.syscall.ArgDigest;
import com.tepeu.syscall.Syscall;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final PersistedApprovalStore store = new PersistedApprovalStore(new InMemoryPersist(), clock);

    @Test
    void askRegistersAndIsIdempotentWhileUndecided() {
        String first = store.ask(ctx("default"), write("a.txt"));
        String again = store.ask(ctx("default"), write("a.txt"));
        assertEquals(first, again);
        assertEquals(1, store.records().size());
        ApprovalRecord record = store.get(first).orElseThrow();
        assertEquals("default", record.sessionId());
        assertEquals("execution.fs.write", record.syscallName());
        assertEquals(ArgDigest.of(Map.of("path", "a.txt")), record.argsDigest());
        assertFalse(record.decided());
        assertFalse(record.consumed());
        assertTrue(record.allow().isEmpty());
    }

    @Test
    void differentSessionOrArgsGetDifferentApprovals() {
        String a = store.ask(ctx("default"), write("a.txt"));
        String b = store.ask(ctx("other"), write("a.txt"));
        String c = store.ask(ctx("default"), write("b.txt"));
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
        assertEquals(3, store.records().size());
    }

    @Test
    void undecidedConsumeIsEmptyAndDecideIsOnce() {
        String id = store.ask(ctx("default"), write("a.txt"));
        assertEquals(Optional.empty(),
                store.consumeDecision(ctx("default"), write("a.txt")));
        store.decide(id, true, "operator");
        assertThrows(IllegalStateException.class, () -> store.decide(id, false, "operator"));
        assertThrows(IllegalArgumentException.class, () -> store.decide("missing", true, "operator"));
    }

    @Test
    void allowConsumesOnceThenExhausted() {
        String id = store.ask(ctx("default"), write("a.txt"));
        store.decide(id, true, "operator");
        assertEquals(Optional.of(true), store.consumeDecision(ctx("default"), write("a.txt")));
        assertEquals(Optional.empty(), store.consumeDecision(ctx("default"), write("a.txt")));
        ApprovalRecord record = store.get(id).orElseThrow();
        assertTrue(record.consumed());
        assertTrue(store.consumeDecision(ctx("other"), write("a.txt")).isEmpty());
    }

    @Test
    void denyFlowsThroughConsume() {
        String id = store.ask(ctx("default"), write("a.txt"));
        store.decide(id, false, "operator");
        assertEquals(Optional.of(false), store.consumeDecision(ctx("default"), write("a.txt")));
    }

    @Test
    void reAskAfterDecideGetsNewApproval() {
        String first = store.ask(ctx("default"), write("a.txt"));
        store.decide(first, false, "operator");
        String second = store.ask(ctx("default"), write("a.txt"));
        assertNotEquals(first, second);
        assertEquals(2, store.records().size());
        assertFalse(store.get(second).orElseThrow().decided());
    }

    @Test
    void askRejectsNull() {
        assertThrows(NullPointerException.class, () -> store.ask(null, write("a.txt")));
        assertThrows(NullPointerException.class,
                () -> store.ask(ctx("default"), null));
    }

    private InvokeContext ctx(String session) {
        return new InvokeContext(
                new Principal(new PrincipalId("u")),
                new WorkspaceId("ws"),
                new SessionId(session));
    }

    private static Syscall write(String path) {
        return new Syscall("execution.fs.write", Map.of("path", path));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
