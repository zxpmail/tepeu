package com.tepeu.os.loop;

import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.memory.InMemorySessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoomLoopApprovalTest {

    @Test
    void thirdStrikeNeedsApprovalThenAllowExecutesViaBus() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("doom-user")),
                Namespace.ofWorkspace(new WorkspaceId("doom-ws")),
                Optional.empty());
        InMemoryApprovalStore approvals = new InMemoryApprovalStore();
        InMemoryCapabilityBus bus = new InMemoryCapabilityBus();
        bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        bus.setApprovalStore(approvals);
        bus.addGuardHook(new DoomLoopGuardHook(store));
        AtomicInteger echo = new AtomicInteger();
        bus.register("echo", (ctx, call) -> {
            echo.incrementAndGet();
            return SyscallResult.success("ok");
        });
        bus.register(SessionLoop.SYSCALL_GENERATE, (ctx, call) -> SyscallResult.success("syscall echo"));

        TurnContext ctx = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        SessionLoop loop = new SessionLoop(store, bus);

        session.inbox().enqueue("q", Optional.empty());
        TurnOutcome blocked = loop.run(ctx, LoopConfig.of("m"));
        assertEquals(TurnOutcome.Kind.FAILED, blocked.kind());
        assertEquals(2, echo.get(), "第三刀未批前不得执行");

        String approvalId = approvals.records().stream()
                .filter(r -> !r.decided())
                .map(ApprovalRecord::approvalId)
                .findFirst()
                .orElseThrow();
        approvals.decide(approvalId, true, "human");

        session.log().append(SessionEventType.TOOL_CALL, "echo", Map.of());
        SyscallResult ok = bus.invoke(ctx, new Syscall("echo", Map.of()));
        assertTrue(ok.ok(), ok.output());
        assertEquals(3, echo.get(), "批准后应执行");

        long approvalResults = session.log().readAll().stream()
                .filter(e -> e.type() == SessionEventType.TOOL_RESULT)
                .filter(e -> "APPROVAL".equals(e.attrs().get("errorCode")))
                .count();
        assertEquals(1, approvalResults);
    }
}
