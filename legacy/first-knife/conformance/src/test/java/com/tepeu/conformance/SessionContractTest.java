package com.tepeu.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.session.ClaimLease;
import com.tepeu.session.Priority;
import com.tepeu.session.Session;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionStore;
import com.tepeu.session.persist.PersistedSessionStore;
import com.tepeu.syscall.Usage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * session 契约。钉账本序号语义与收件箱领取语义：
 * seq 从 1 起严格递增无空洞、跨重连续号；open 主人/工作区不一致 fail fast；
 * 收件箱按 NOW→NEXT→LATER 领取，租约过期可重领，ack 后不再领取，nack 归队。
 */
class SessionContractTest {

    @TempDir
    Path dir;

    @Test
    void seqIsPerBookFromOneWithoutGaps() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = open(persist, Clock.systemUTC());
            long e1 = session.log().append(SessionEventType.USER_MESSAGE, "1");
            long e2 = session.log().append(SessionEventType.USER_MESSAGE, "2");
            long ledgerSeq = session.ledger().record("llm.generate", new Usage(1, 1));
            long auditSeq = session.audit().record("operator", "act", "d");
            assertEquals(List.of(1L, 2L), List.of(e1, e2));
            assertEquals(1L, ledgerSeq);
            assertEquals(1L, auditSeq);
            assertEquals(List.of(1L, 2L),
                    session.log().readAll().stream().map(SessionEvent::seq).toList());
        }
    }

    @Test
    void seqContinuesAfterReopen() throws Exception {
        Path db = dir.resolve("s.db");
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            open(persist, Clock.systemUTC()).log().append(SessionEventType.USER_MESSAGE, "1");
        }
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            Session session = open(persist, Clock.systemUTC());
            long seq = session.log().append(SessionEventType.USER_MESSAGE, "2");
            assertEquals(2, seq);
        }
    }

    @Test
    void logGetReturnsBySeq() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = open(persist, Clock.systemUTC());
            session.log().append(SessionEventType.USER_MESSAGE, "one");
            session.log().append(SessionEventType.ASSISTANT_MESSAGE, "two");
            assertEquals("two", session.log().get(2).orElseThrow().body());
            assertTrue(session.log().get(9).isEmpty());
        }
    }

    @Test
    void openMismatchFailsFast() throws Exception {
        Path db = dir.resolve("s.db");
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            open(persist, Clock.systemUTC());
        }
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            SessionStore store = new PersistedSessionStore(persist);
            assertThrows(IllegalStateException.class, () -> store.open(
                    SessionStore.DEFAULT,
                    new Principal(new PrincipalId("someone-else")),
                    new WorkspaceId("ws")));
            assertThrows(IllegalStateException.class, () -> store.open(
                    SessionStore.DEFAULT,
                    new Principal(new PrincipalId("u")),
                    new WorkspaceId("elsewhere")));
        }
    }

    @Test
    void inboxClaimsByPriorityAndAcks() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            MutableClock clock = new MutableClock();
            Session session = open(persist, clock);
            session.inbox().enqueue("later", Priority.LATER);
            session.inbox().enqueue("next", Priority.NEXT);
            session.inbox().enqueue("now", Priority.NOW);

            ClaimLease lease = session.inbox().claimNext().orElseThrow();
            assertEquals("now", session.inbox().claimed(lease.claimId()).orElseThrow().body());
            session.inbox().ack(lease.claimId());

            lease = session.inbox().claimNext().orElseThrow();
            assertEquals("next", session.inbox().claimed(lease.claimId()).orElseThrow().body());
            session.inbox().ack(lease.claimId());

            lease = session.inbox().claimNext().orElseThrow();
            session.inbox().ack(lease.claimId());
            assertTrue(session.inbox().claimNext().isEmpty());
        }
    }

    @Test
    void expiredLeaseIsReclaimable() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            MutableClock clock = new MutableClock();
            Session session = open(persist, clock);
            session.inbox().enqueue("only");
            ClaimLease first = session.inbox().claimNext().orElseThrow();
            clock.advance(ClaimLease.DEFAULT_TTL.plusSeconds(1));
            ClaimLease second = session.inbox().claimNext().orElseThrow();
            assertEquals(first.messageId(), second.messageId());
        }
    }

    @Test
    void nackReturnsMessageToQueue() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            MutableClock clock = new MutableClock();
            Session session = open(persist, clock);
            session.inbox().enqueue("retry-me");
            ClaimLease lease = session.inbox().claimNext().orElseThrow();
            session.inbox().nack(lease.claimId());
            assertTrue(session.inbox().claimNext().isPresent());
        }
    }

    @Test
    void registersOverrideByKey() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = open(persist, Clock.systemUTC());
            session.registers().put("loop.state", "running");
            session.registers().put("loop.state", "idle");
            assertEquals("idle", session.registers().get("loop.state").orElseThrow());
            session.registers().put("other", "x");
            assertEquals("idle", session.registers().snapshot().get("loop.state"));
            assertFalse(session.registers().snapshot().containsKey("nope"));
        }
    }

    private Session open(SqlitePersist persist, Clock clock) {
        return new PersistedSessionStore(persist, clock).open(
                SessionStore.DEFAULT, new Principal(new PrincipalId("u")), new WorkspaceId("ws"));
    }

    /** 可推进的冻结起点时钟，供租约过期用。 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-14T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
