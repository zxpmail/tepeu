package com.tepeu.dispatch;

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
import com.tepeu.policy.PolicyVerdict;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DispatchTest {

    private final InvokeContext ctx = new InvokeContext(
            new Principal(new PrincipalId("u")),
            new WorkspaceId("ws"),
            new SessionId("default"));

    @Test
    void gateOrderCancelThenPolicyThenRegistry() {
        Dispatch noPolicy = new Dispatch(null, null);
        assertEquals(Dispatch.CANCELLED, noPolicy.invoke(cancelled(), "llm.generate", Map.of())
                .errorCode().orElseThrow());
        assertEquals(Dispatch.DENIED, noPolicy.invoke(ctx, "llm.generate", Map.of())
                .errorCode().orElseThrow());
        Dispatch matrix = new Dispatch(new DefaultRuleMatrix(), null);
        assertEquals(Dispatch.DENIED, matrix.invoke(ctx, "unknown.op", Map.of())
                .errorCode().orElseThrow());
        Dispatch allowAll = new Dispatch((c, s) -> PolicyVerdict.ALLOW, null);
        assertEquals(Dispatch.NOT_FOUND, allowAll.invoke(ctx, "nothing", Map.of())
                .errorCode().orElseThrow());
    }

    @Test
    void denyStopsHandler() {
        AtomicBoolean reached = new AtomicBoolean(false);
        Dispatch d = new Dispatch(new DefaultRuleMatrix(), null);
        d.register("execution.fs.delete", (c, s) -> {
            reached.set(true);
            return SyscallResult.success("deleted");
        });
        SyscallResult r = d.invoke(ctx, "execution.fs.delete", Map.of("path", "a.txt"));
        assertFalse(r.ok());
        assertEquals(Dispatch.DENIED, r.errorCode().orElseThrow());
        assertFalse(reached.get());
    }

    @Test
    void allowReachesHandlerAndPassesResultThrough() {
        Dispatch d = new Dispatch(new DefaultRuleMatrix(), null);
        AtomicReference<Syscall> seen = new AtomicReference<>();
        d.register("llm.generate", (c, s) -> {
            seen.set(s);
            return SyscallResult.success("ok", new Usage(1, 2));
        });
        SyscallResult r = d.invoke(ctx, "llm.generate", Map.of("model", "fake"));
        assertTrue(r.ok());
        assertEquals("ok", r.output());
        assertEquals("fake", seen.get().args().get("model"));
        assertEquals(3, r.usage().orElseThrow().totalTokens());
    }

    @Test
    void approvalFlowSingleUse() {
        FakeApprovalStore approvals = new FakeApprovalStore();
        Dispatch d = new Dispatch((c, s) -> PolicyVerdict.NEED_APPROVAL, approvals);
        d.register("execution.fs.write", (c, s) -> SyscallResult.success("written"));

        SyscallResult first = d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt"));
        assertEquals(Dispatch.APPROVAL_REQUIRED, first.errorCode().orElseThrow());
        String id = first.output();

        assertEquals(id, d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt")).output());

        approvals.decide(id, false, "operator");
        assertEquals(Dispatch.DENIED, d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt"))
                .errorCode().orElseThrow());

        SyscallResult reask = d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt"));
        assertEquals(Dispatch.APPROVAL_REQUIRED, reask.errorCode().orElseThrow());
        assertNotEquals(id, reask.output());

        approvals.decide(reask.output(), true, "operator");
        assertTrue(d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt")).ok());
        assertEquals(Dispatch.APPROVAL_REQUIRED, d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt"))
                .errorCode().orElseThrow());
    }

    @Test
    void differentSessionOrArgsAskSeparately() {
        FakeApprovalStore approvals = new FakeApprovalStore();
        Dispatch d = new Dispatch((c, s) -> PolicyVerdict.NEED_APPROVAL, approvals);
        InvokeContext other = new InvokeContext(
                new Principal(new PrincipalId("u")),
                new WorkspaceId("ws"),
                new SessionId("other"));
        String a = d.invoke(ctx, "execution.fs.write", Map.of("path", "a.txt")).output();
        String b = d.invoke(other, "execution.fs.write", Map.of("path", "a.txt")).output();
        String c = d.invoke(ctx, "execution.fs.write", Map.of("path", "b.txt")).output();
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    void needApprovalWithoutChannelIsDenied() {
        Dispatch d = new Dispatch((c, s) -> PolicyVerdict.NEED_APPROVAL, null);
        assertEquals(Dispatch.DENIED, d.invoke(ctx, "execution.fs.write", Map.of())
                .errorCode().orElseThrow());
    }

    @Test
    void handlerThrowBecomesHandlerError() {
        Dispatch d = new Dispatch(new DefaultRuleMatrix(), null);
        d.register("llm.generate", (c, s) -> {
            throw new IllegalStateException("boom");
        });
        SyscallResult r = d.invoke(ctx, "llm.generate", Map.of());
        assertFalse(r.ok());
        assertEquals(Dispatch.HANDLER_ERROR, r.errorCode().orElseThrow());
        assertEquals("IllegalStateException", r.output());
    }

    @Test
    void duplicateRegisterFailsAndNamesSortedImmutable() {
        Dispatch d = new Dispatch(new DefaultRuleMatrix(), null);
        d.register("b", (c, s) -> SyscallResult.success("x"));
        d.register("a", (c, s) -> SyscallResult.success("y"));
        assertThrows(IllegalStateException.class,
                () -> d.register("a", (c, s) -> SyscallResult.success("z")));
        assertEquals(List.of("a", "b"), d.names());
        assertThrows(UnsupportedOperationException.class, () -> d.names().add("c"));
    }

    private InvokeContext cancelled() {
        InvokeContext cancelled = new InvokeContext(
                new Principal(new PrincipalId("u")),
                new WorkspaceId("ws"),
                new SessionId("default"));
        cancelled.cancel();
        return cancelled;
    }
}
