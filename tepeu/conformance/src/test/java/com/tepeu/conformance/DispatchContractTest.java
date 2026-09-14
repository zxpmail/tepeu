package com.tepeu.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.dispatch.Dispatch;
import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.policy.PolicyHook;
import com.tepeu.policy.PolicyVerdict;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.policy.persist.PersistedApprovalStore;
import com.tepeu.syscall.SyscallResult;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * dispatch 契约。对真栈（DefaultRuleMatrix + PersistedApprovalStore + sqlite）钉门语义：
 * 放行到 handler、需审批先登记后单次放行、未登记名拒之门外、
 * 词汇表外但策略放行的名 NOT_FOUND、取消先于一切。
 */
class DispatchContractTest {

    @TempDir
    Path dir;

    private InvokeContext ctx() {
        return new InvokeContext(
                new Principal(new PrincipalId("u")), new WorkspaceId("ws"), new SessionId("default"));
    }

    @Test
    void allowedNameReachesHandler() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), new PersistedApprovalStore(persist));
            AtomicInteger calls = new AtomicInteger();
            dispatch.register("execution.sandbox.probe", (c, s) -> {
                calls.incrementAndGet();
                return SyscallResult.success("ok");
            });
            SyscallResult result = dispatch.invoke(ctx(), "execution.sandbox.probe", Map.of());
            assertTrue(result.ok());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void approvalFlowSingleUseThenAskAgain() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), approvals);
            AtomicInteger writes = new AtomicInteger();
            dispatch.register("execution.fs.write", (c, s) -> {
                writes.incrementAndGet();
                return SyscallResult.success("written");
            });
            InvokeContext ctx = ctx();
            Map<String, String> args = Map.of("path", "x.txt");

            SyscallResult first = dispatch.invoke(ctx, "execution.fs.write", args);
            assertEquals(Dispatch.APPROVAL_REQUIRED, first.errorCode().orElseThrow());
            assertEquals(0, writes.get());
            String approvalId = first.output();
            approvals.decide(approvalId, true, "operator");

            SyscallResult second = dispatch.invoke(ctx, "execution.fs.write", args);
            assertEquals("written", second.output());
            assertEquals(1, writes.get());

            SyscallResult third = dispatch.invoke(ctx, "execution.fs.write", args);
            assertEquals(Dispatch.APPROVAL_REQUIRED, third.errorCode().orElseThrow());
            assertNotEquals(approvalId, third.output());
        }
    }

    @Test
    void unregisteredNameIsDeniedBeforeRegistry() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), new PersistedApprovalStore(persist));
            SyscallResult result = dispatch.invoke(ctx(), "kernel.memory.write", Map.of());
            assertEquals(Dispatch.DENIED, result.errorCode().orElseThrow());
        }
    }

    @Test
    void policyAllowedButNotRegisteredIsNotFound() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            PolicyHook allowAll = (ctx, syscall) -> PolicyVerdict.ALLOW;
            Dispatch dispatch = new Dispatch(allowAll, new PersistedApprovalStore(persist));
            SyscallResult result = dispatch.invoke(ctx(), "no.such.handler", Map.of());
            assertEquals(Dispatch.NOT_FOUND, result.errorCode().orElseThrow());
        }
    }

    @Test
    void cancelBeatsEverything() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), new PersistedApprovalStore(persist));
            dispatch.register("execution.sandbox.probe", (c, s) -> SyscallResult.success("ok"));
            InvokeContext ctx = ctx();
            ctx.cancel();
            SyscallResult result = dispatch.invoke(ctx, "execution.sandbox.probe", Map.of());
            assertEquals(Dispatch.CANCELLED, result.errorCode().orElseThrow());
        }
    }
}
