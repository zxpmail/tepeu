package com.tepeu.os.policy.persist;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.persist.sqlite.SqliteDataSources;
import org.springframework.transaction.TransactionException;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.conformance.ApprovalConformance;
import com.tepeu.os.syscall.Syscall;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalPersistPortsTest {

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
    Stream<DynamicTest> approvalConformance() {
        return StreamSupport.stream(ApprovalConformance.suite(ApprovalPersistPortsTest::openStore).spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }

    @Test
    void restartKeepsAskedAndFailClosedAfterClose() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-appr-restart-");
        TurnContext ctx = turn("s1");
        Syscall call = new Syscall("dangerous", Map.of());
        String approvalId;
        try (var persist = SqliteDataSources.access(dir.resolve("approvals.sqlite"))) {
            ApprovalStore store = ApprovalPersistence.open(persist);
            approvalId = store.ask(ctx, call);
            assertEquals(approvalId, store.ask(ctx, call));
        }
        try (var persist = SqliteDataSources.access(dir.resolve("approvals.sqlite"))) {
            ApprovalStore store = ApprovalPersistence.open(persist);
            assertEquals(approvalId, store.ask(ctx, call));
            store.decide(approvalId, true, "host");
            assertEquals(true, store.consumeDecision(ctx, call).orElseThrow());
            assertTrue(store.consumeDecision(ctx, call).isEmpty());
        }
        var persist = SqliteDataSources.access(dir.resolve("approvals.sqlite"));
        ApprovalStore closed = ApprovalPersistence.open(persist);
        persist.close();
        assertThrows(TransactionException.class, () -> closed.ask(ctx, call));
    }

    private static ApprovalStore openStore() {
        try {
            Path dir = Files.createTempDirectory("tepeu-appr-");
            var persist = SqliteDataSources.access(dir.resolve("approvals.sqlite"));
            OPEN.add(persist);
            return ApprovalPersistence.open(persist);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TurnContext turn(String session) {
        Principal owner = Principal.personal(new PrincipalId("appr-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("appr-ws"));
        return new TurnContext(owner, ns, new SessionId(session), Optional.empty());
    }
}
