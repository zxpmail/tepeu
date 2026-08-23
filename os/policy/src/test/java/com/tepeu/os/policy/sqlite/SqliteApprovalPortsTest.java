package com.tepeu.os.policy.sqlite;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
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

class SqliteApprovalPortsTest {

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
        return StreamSupport.stream(ApprovalConformance.suite(() -> openStore()).spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }

    @Test
    void restartKeepsAskedAndFailClosedAfterClose() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-appr-restart-");
        Path db = dir.resolve("approvals.sqlite");
        TurnContext ctx = turn("s1");
        Syscall call = new Syscall("dangerous", Map.of());
        String approvalId;
        try (SqliteApprovalStore store = new SqliteApprovalStore(db)) {
            approvalId = store.ask(ctx, call);
            assertEquals(approvalId, store.ask(ctx, call));
        }
        try (SqliteApprovalStore store = new SqliteApprovalStore(db)) {
            assertEquals(approvalId, store.ask(ctx, call));
            store.decide(approvalId, true, "host");
            assertEquals(true, store.consumeDecision(ctx, call).orElseThrow());
            assertTrue(store.consumeDecision(ctx, call).isEmpty());
        }
        SqliteApprovalStore closed = new SqliteApprovalStore(db);
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.ask(ctx, call));
    }

    private static SqliteApprovalStore openStore() {
        try {
            Path dir = Files.createTempDirectory("tepeu-appr-");
            SqliteApprovalStore store = new SqliteApprovalStore(dir.resolve("approvals.sqlite"));
            OPEN.add(store);
            return store;
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
