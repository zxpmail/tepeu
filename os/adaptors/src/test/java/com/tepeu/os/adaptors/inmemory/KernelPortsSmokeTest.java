package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.bus.CapabilityBus;
import com.tepeu.os.kernel.bus.Syscall;
import com.tepeu.os.kernel.bus.SyscallResult;
import com.tepeu.os.kernel.context.TurnContext;
import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.identity.PrincipalId;
import com.tepeu.os.kernel.identity.WorkspaceId;
import com.tepeu.os.kernel.session.ClaimLease;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionEventType;
import com.tepeu.os.kernel.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内核端口冒烟：Inbox claim → 总线 syscall → 会话日志。
 */
class KernelPortsSmokeTest {

    @Test
    void inboxClaimThenSyscallAppendsLog() {
        SessionRegistry registry = new InMemorySessionRegistry();
        Principal owner = Principal.personal(new PrincipalId("user-1"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ws-1"));
        Session session = registry.create(owner, ns, Optional.empty());

        String msgId = session.inbox().enqueue("hello", Optional.of("user"));
        Optional<ClaimLease> lease = session.inbox().claimNext();
        assertTrue(lease.isPresent());
        assertEquals(msgId, lease.get().messageId());

        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        CapabilityBus bus = new InMemoryCapabilityBus();
        bus.register("echo", (c, call) -> SyscallResult.success(call.args().getOrDefault("text", "")));
        SyscallResult result = bus.invoke(ctx, new Syscall("echo", Map.of("text", "hello")));
        assertTrue(result.ok());

        session.log().append(SessionEventType.USER_MESSAGE, "hello", Map.of());
        session.log().append(SessionEventType.ASSISTANT_MESSAGE, result.output(), Map.of());
        session.inbox().ack(lease.get().claimId());

        assertEquals(2, session.log().readAll().size());
    }

    @Test
    void policyDenyFailsClosed() {
        TurnContext ctx = newTurnContext();
        CapabilityBus bus = new InMemoryCapabilityBus();
        bus.register("echo", (c, call) -> SyscallResult.success("ok"));
        bus.setPolicyHook((c, call) -> com.tepeu.os.kernel.bus.PolicyVerdict.DENY);
        assertThrows(com.tepeu.os.kernel.bus.PolicyDeniedException.class,
                () -> bus.invoke(ctx, new Syscall("echo", Map.of())));

        // 策略钩子抛异常 → 规范化为 DENY，不穿透
        bus.setPolicyHook((c, call) -> { throw new IllegalStateException("boom"); });
        com.tepeu.os.kernel.bus.PolicyDeniedException ex =
                assertThrows(com.tepeu.os.kernel.bus.PolicyDeniedException.class,
                        () -> bus.invoke(ctx, new Syscall("echo", Map.of())));
        assertEquals(com.tepeu.os.kernel.bus.PolicyVerdict.DENY, ex.verdict());
    }

    @Test
    void handlerExceptionBecomesFailureResult() {
        TurnContext ctx = newTurnContext();
        CapabilityBus bus = new InMemoryCapabilityBus();
        bus.register("boom", (c, call) -> { throw new IllegalArgumentException("bad"); });
        SyscallResult result = bus.invoke(ctx, new Syscall("boom", Map.of()));
        assertFalse(result.ok());
        assertEquals("HANDLER_ERROR", result.errorCode().orElse(""));

        bus.register("null", (c, call) -> null);
        SyscallResult nullResult = bus.invoke(ctx, new Syscall("null", Map.of()));
        assertFalse(nullResult.ok());
    }

    @Test
    void guardFailureFailsClosedAndCancelRejects() {
        TurnContext ctx = newTurnContext();
        CapabilityBus bus = new InMemoryCapabilityBus();
        bus.register("echo", (c, call) -> SyscallResult.success("ok"));
        bus.addGuardHook(new com.tepeu.os.kernel.bus.GuardHook() {
            @Override
            public void before(com.tepeu.os.kernel.context.TurnContext c,
                    com.tepeu.os.kernel.bus.Syscall call) {
                throw new IllegalStateException("guard infra broken");
            }
        });
        assertThrows(com.tepeu.os.kernel.bus.BusGuardException.class,
                () -> bus.invoke(ctx, new Syscall("echo", Map.of())));

        TurnContext cancelled = newTurnContext();
        cancelled.cancel();
        assertThrows(com.tepeu.os.kernel.bus.BusGuardException.class,
                () -> bus.invoke(cancelled, new Syscall("echo", Map.of())));
    }

    @Test
    void duplicateRegistrationRejected() {
        CapabilityBus bus = new InMemoryCapabilityBus();
        bus.register("echo", (c, call) -> SyscallResult.success("ok"));
        assertThrows(IllegalStateException.class,
                () -> bus.register("echo", (c, call) -> SyscallResult.success("dup")));
    }

    @Test
    void replaceRangeKeepsAuditLogAndReturnsSurface() {
        SessionRegistry registry = new InMemorySessionRegistry();
        Session session = registry.create(Principal.personal(new PrincipalId("user-1")),
                Namespace.ofWorkspace(new WorkspaceId("ws-1")), Optional.empty());
        session.log().append(SessionEventType.USER_MESSAGE, "m1", Map.of());
        session.log().append(SessionEventType.ASSISTANT_MESSAGE, "m2", Map.of());
        session.log().append(SessionEventType.USER_MESSAGE, "m3", Map.of());

        session.logReplace().replaceRange(1, 2, "summary of 1-2");

        // 审计：底层日志保留全部 + 1 条 checkpoint
        assertEquals(4, session.log().readAll().size());
        // surface：被遮蔽区间不可见，checkpoint + 其余事件可见
        List<com.tepeu.os.kernel.session.SessionEvent> surface = session.logReplace().surface();
        assertEquals(2, surface.size());
        assertEquals(SessionEventType.COMPACTION_CHECKPOINT, surface.get(0).type());
        assertEquals("m3", surface.get(1).body());
        // 追加新事件落 surface 尾部
        session.log().append(SessionEventType.USER_MESSAGE, "m4", Map.of());
        assertEquals(3, session.logReplace().surface().size());
    }

    @Test
    void nackReturnsMessageToQueue() {
        SessionRegistry registry = new InMemorySessionRegistry();
        Session session = registry.create(Principal.personal(new PrincipalId("user-1")),
                Namespace.ofWorkspace(new WorkspaceId("ws-1")), Optional.empty());
        String msgId = session.inbox().enqueue("hello", Optional.of("user"));
        ClaimLease lease = session.inbox().claimNext().orElseThrow();
        session.inbox().nack(lease.claimId());
        ClaimLease reLease = session.inbox().claimNext().orElseThrow();
        assertEquals(msgId, reLease.messageId());
    }

    private static TurnContext newTurnContext() {
        Principal owner = Principal.personal(new PrincipalId("user-1"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ws-1"));
        return new TurnContext(owner, ns, new com.tepeu.os.kernel.session.SessionId("s-1"),
                Optional.empty());
    }
}
