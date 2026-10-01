package com.tepeu.policy.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.syscall.Syscall;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalSqliteTest {

    @Test
    void approvalsSurviveReopenAndStaySingleUse(@TempDir Path dir) {
        Path file = dir.resolve("approvals.db");
        InvokeContext ctx = new InvokeContext(
                new Principal(new PrincipalId("u")),
                new WorkspaceId("ws"),
                new SessionId("default"));
        Syscall syscall = new Syscall("execution.fs.write", Map.of("path", "a.txt"));
        String id;
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            PersistedApprovalStore store = new PersistedApprovalStore(persist);
            id = store.ask(ctx, syscall);
            store.decide(id, true, "operator");
        }
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            PersistedApprovalStore store = new PersistedApprovalStore(persist);
            assertEquals(1, store.records().size());
            assertEquals("operator", store.get(id).orElseThrow().decidedBy().orElseThrow());
            assertEquals(Optional.of(true), store.consumeDecision(ctx, syscall));
            assertEquals(Optional.empty(), store.consumeDecision(ctx, syscall));
            assertTrue(store.records().get(0).consumed());
        }
    }
}
