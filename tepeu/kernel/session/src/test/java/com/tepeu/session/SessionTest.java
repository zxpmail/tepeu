package com.tepeu.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.session.persist.PersistedSessionStore;
import com.tepeu.syscall.Usage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final PersistedSessionStore store = new PersistedSessionStore(new InMemoryPersist(), clock);

    @Test
    void openCreatesThenReopenKeepsIdentityAndLog() {
        Session first = open(SessionStore.DEFAULT);
        first.log().append(SessionEventType.USER_MESSAGE, "hi");
        first.registers().put("loop.state", "IDLE");
        Session again = open(SessionStore.DEFAULT);
        assertEquals(new PrincipalId("u"), again.owner().id());
        assertEquals(new WorkspaceId("ws"), again.workspace());
        assertEquals("hi", again.log().get(1).orElseThrow().body());
        assertEquals("IDLE", again.registers().get("loop.state").orElseThrow());
        Session other = open(new SessionId("other"));
        assertTrue(other.log().readAll().isEmpty());
    }

    @Test
    void logLedgerAuditAreSeparateAndOrdered() {
        Session session = open(SessionStore.DEFAULT);
        assertEquals(1, session.log().append(SessionEventType.USER_MESSAGE, "a"));
        assertEquals(2, session.log().append(SessionEventType.ASSISTANT_MESSAGE, "b"));
        session.ledger().record("llm.generate", new Usage(3, 5));
        session.ledger().record("llm.generate", new Usage(1, 1, Optional.of("0.01")));
        session.audit().record("u", "approve", "write");
        assertEquals(List.of("a", "b"), session.log().readAll().stream().map(SessionEvent::body).toList());
        assertEquals(1, session.audit().readAll().size());
        assertEquals("approve", session.audit().readAll().get(0).action());
        assertEquals(10, session.ledger().readAll().stream().mapToLong(e -> e.usage().totalTokens()).sum());
        assertEquals("0.01", session.ledger().readAll().get(1).usage().cost().orElseThrow());
        assertTrue(session.log().get(9).isEmpty());
        assertTrue(session.log().get(1).orElseThrow().attrs().isEmpty());
    }

    @Test
    void logAttrsRoundTripAndRejectBlankKey() {
        Session session = open(SessionStore.DEFAULT);
        Map<String, String> raw = new HashMap<>();
        raw.put("callId", "c1");
        raw.put("name", "fs.write");
        session.log().append(SessionEventType.TOOL_CALL, "{}", raw);
        raw.put("callId", "other");
        SessionEvent event = session.log().get(1).orElseThrow();
        assertEquals("c1", event.attr("callId").orElseThrow());
        assertEquals("fs.write", event.attr("name").orElseThrow());
        assertTrue(event.attr("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> event.attrs().put("x", "y"));
        assertThrows(IllegalArgumentException.class,
                () -> session.log().append(SessionEventType.TOOL_RESULT, "ok", Map.of(" ", "x")));
    }

    @Test
    void registersOverwriteAndRejectBlank() {
        Session session = open(SessionStore.DEFAULT);
        session.registers().put("loop.state", "RUN");
        session.registers().put("loop.state", "IDLE");
        assertEquals("IDLE", session.registers().snapshot().get("loop.state"));
        assertThrows(IllegalArgumentException.class, () -> session.registers().put(" ", "x"));
    }

    @Test
    void inboxClaimsNowBeforeNextThenAck() {
        Session session = open(SessionStore.DEFAULT);
        session.inbox().enqueue("later", Priority.LATER);
        session.inbox().enqueue("next");
        session.inbox().enqueue("now", Priority.NOW);
        ClaimLease first = session.inbox().claimNext().orElseThrow();
        assertEquals("now", session.inbox().claimed(first.claimId()).orElseThrow().body());
        session.inbox().ack(first.claimId());
        assertEquals("next", session.inbox().claimed(session.inbox().claimNext().orElseThrow().claimId())
                .orElseThrow().body());
    }

    @Test
    void nackAndExpiredLeaseCanBeClaimedAgain() {
        Session session = open(SessionStore.DEFAULT);
        session.inbox().enqueue("one");
        ClaimLease lease = session.inbox().claimNext().orElseThrow();
        session.inbox().nack(lease.claimId());
        ClaimLease again = session.inbox().claimNext().orElseThrow();
        assertEquals("one", session.inbox().claimed(again.claimId()).orElseThrow().body());
        clock.advance(ClaimLease.DEFAULT_TTL.plusSeconds(1));
        ClaimLease stolen = session.inbox().claimNext().orElseThrow();
        session.inbox().ack(stolen.claimId());
        assertTrue(session.inbox().claimNext().isEmpty());
        assertThrows(IllegalStateException.class, () -> session.inbox().ack(stolen.claimId()));
    }

    private Session open(SessionId id) {
        return store.open(id, new Principal(new PrincipalId("u")), new WorkspaceId("ws"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
