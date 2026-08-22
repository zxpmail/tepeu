package com.tepeu.os.compose;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * compose 冒烟：接线后能跑总线和 Loop 答复路径。
 */
class MemoryAssemblyTest {

    @Test
    void wiresSessionAndAllowBus() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("compose-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("compose-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());

        wired.bus().setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        wired.bus().register("echo", (ctx, call) ->
                SyscallResult.success(call.args().getOrDefault("text", "")));

        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        SyscallResult r = wired.bus().invoke(ctx, new Syscall("echo", Map.of("text", "ok")));
        assertTrue(r.ok());
        assertEquals("ok", r.output());
        assertEquals(session.id(), ctx.sessionId());
        assertTrue(session.parentId().isEmpty());
    }

    @Test
    void wiresLoopReplyPath() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("loop-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("loop-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        wired.bus().setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        session.inbox().enqueue("hi", Optional.of("user"));

        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        TurnOutcome o = wired.loop().run(ctx, LoopConfig.of("fake-model"));
        assertTrue(o.completed(), o.detail());
        assertEquals(2, session.log().readAll().size());
        assertEquals(SessionEventType.USER_MESSAGE, session.log().readAll().get(0).type());
        assertEquals(SessionEventType.ASSISTANT_MESSAGE, session.log().readAll().get(1).type());
        assertEquals(FakeLlmTransport.OUTPUT, session.log().readAll().get(1).body());
    }
}
