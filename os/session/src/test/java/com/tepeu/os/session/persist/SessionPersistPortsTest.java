package com.tepeu.os.session.persist;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.persist.sqlite.SqliteDataSources;
import org.springframework.transaction.TransactionException;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.conformance.SessionConformance;
import com.tepeu.os.syscall.Usage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPersistPortsTest {

    private static final List<AutoCloseable> OPEN = new CopyOnWriteArrayList<>();

    @AfterAll
    static void closeStores() {
        for (AutoCloseable c : OPEN) {
            try {
                c.close();
            } catch (Exception ignored) {
                // Windows WAL
            }
        }
        OPEN.clear();
    }

    @TestFactory
    Stream<DynamicTest> sessionConformance() {
        SessionConformance.SessionFactory factory = clock -> {
            SessionPersistence persistence = open(clock);
            return persistence.sessions().create(owner(), ns(), Optional.empty());
        };
        return toDynamicTests(SessionConformance.suite(factory));
    }

    @TestFactory
    Stream<DynamicTest> forkConformance() {
        SessionConformance.StoreFactory stores = clock -> open(clock).sessions();
        return toDynamicTests(SessionConformance.forkSuite(stores));
    }

    @Test
    void restartKeepsEventsForkSeedAndRecover() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-restart-");
        SessionId id;
        SessionId childId;
        String digest;
        try (var kernel = SqliteDataSources.access(dir.resolve("kernel.sqlite"))) {
            SessionPersistence persistence = SessionPersistence.open(kernel);
            SessionStore store = persistence.sessions();
            Session session = store.create(owner(), ns(), Optional.empty());
            id = session.id();
            session.log().append(SessionEventType.USER_MESSAGE, "hello", Map.of());
            session.log().append(SessionEventType.TOOL_CALL, "open", Map.of());
            digest = session.blobs().put("blob-bytes".getBytes());
            persistence.audit().record("sqlite-user", "test.audit", "persist", Map.of());
            Session child = store.fork(id, 1);
            childId = child.id();
            assertEquals(2L, child.seedEndSeq().orElse(-1L));
            assertEquals("test.audit", persistence.audit().readAll().get(0).action());
        }
        try (var kernel = SqliteDataSources.access(dir.resolve("kernel.sqlite"))) {
            SessionPersistence persistence = SessionPersistence.open(kernel);
            SessionStore store = persistence.sessions();
            Session session = store.get(id).orElseThrow();
            assertEquals("hello", session.log().readAll().get(0).body());
            assertEquals(1, session.recover());
            assertEquals("INTERRUPTED", session.log().readAll().get(2).attrs().get("errorCode"));
            assertTrue(session.blobs().get(digest).isPresent());
            assertEquals("test.audit", persistence.audit().readAll().get(0).action());
            Session child = store.get(childId).orElseThrow();
            assertEquals(2L, child.seedEndSeq().orElse(-1L));
            assertThrows(IllegalArgumentException.class,
                    () -> child.logReplace().replaceRange(1, 1, "no-seed"));
        }
    }

    @Test
    void ledgerReadYourWritesThenFailClosedAfterClose() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-ledger-");
        var persist = SqliteDataSources.access(dir.resolve("kernel.sqlite"));
        SessionPersistence persistence = SessionPersistence.open(persist);
        Session session = persistence.sessions().create(owner(), ns(), Optional.empty());
        long seq = session.ledger().record("llm.generate", new Usage(1, 2, 0, 0), Map.of());
        assertEquals(1L, seq);
        assertEquals(1, session.ledger().readAll().size());
        assertEquals(3, session.ledger().readAll().get(0).usage().totalTokens());
        persist.close();
        assertThrows(TransactionException.class,
                () -> session.ledger().record("llm.generate", new Usage(1, 0, 0, 0), Map.of()));
    }

    private static SessionPersistence open(Clock clock) {
        try {
            Path dir = Files.createTempDirectory("tepeu-sess-");
            var persist = SqliteDataSources.access(dir.resolve("kernel.sqlite"));
            OPEN.add(persist);
            return SessionPersistence.open(persist, clock);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Principal owner() {
        return Principal.personal(new PrincipalId("sqlite-user"));
    }

    private static Namespace ns() {
        return Namespace.ofWorkspace(new WorkspaceId("sqlite-ws"));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
