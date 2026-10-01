package com.tepeu.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.policy.persist.PersistedApprovalStore;
import com.tepeu.syscall.Syscall;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 审批契约。钉单次许可语义：ask 幂等（未决指向同单）、decide 一次、
 * consume 取走即消费、消费后再调须重新审批、许可绑参数指纹不绑名字。
 */
class ApprovalContractTest {

    @TempDir
    Path dir;

    private static final InvokeContext CTX = new InvokeContext(
            new Principal(new PrincipalId("u")), new WorkspaceId("ws"), new SessionId("default"));

    @Test
    void askIsIdempotentWhilePending() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            Syscall call = new Syscall("execution.fs.write", Map.of("path", "x.txt"));
            String first = approvals.ask(CTX, call);
            String second = approvals.ask(CTX, call);
            assertEquals(first, second);
        }
    }

    @Test
    void decideHappensOnce() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            String id = approvals.ask(CTX, new Syscall("n", Map.of()));
            approvals.decide(id, true, "operator");
            assertThrows(IllegalStateException.class,
                    () -> approvals.decide(id, false, "operator"));
        }
    }

    @Test
    void consumeTakesDecisionAway() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            Syscall call = new Syscall("n", Map.of());
            String id = approvals.ask(CTX, call);
            approvals.decide(id, true, "operator");
            assertEquals(true, approvals.consumeDecision(CTX, call).orElseThrow());
            assertTrue(approvals.consumeDecision(CTX, call).isEmpty());
        }
    }

    @Test
    void denyDecisionAlsoConsumableOnce() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            Syscall call = new Syscall("n", Map.of());
            String id = approvals.ask(CTX, call);
            approvals.decide(id, false, "operator");
            assertEquals(false, approvals.consumeDecision(CTX, call).orElseThrow());
        }
    }

    @Test
    void consumedApprovalMeansAskAgain() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            Syscall call = new Syscall("n", Map.of());
            String first = approvals.ask(CTX, call);
            approvals.decide(first, true, "operator");
            approvals.consumeDecision(CTX, call);
            String second = approvals.ask(CTX, call);
            assertNotEquals(first, second);
        }
    }

    @Test
    void approvalBindsArgsDigestNotJustName() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            String id = approvals.ask(CTX, new Syscall("n", Map.of("path", "a.txt")));
            approvals.decide(id, true, "operator");
            assertEquals(true, approvals.consumeDecision(CTX,
                    new Syscall("n", Map.of("path", "a.txt"))).orElseThrow());
            assertTrue(approvals.consumeDecision(CTX,
                    new Syscall("n", Map.of("path", "b.txt"))).isEmpty());
        }
    }
}
