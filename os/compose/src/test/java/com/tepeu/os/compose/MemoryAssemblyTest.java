package com.tepeu.os.compose;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.llm.local.FakeLlmTransport;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.orchestration.AssembledPrompt;
import com.tepeu.os.orchestration.CommandKind;
import com.tepeu.os.orchestration.CommandResult;
import com.tepeu.os.orchestration.Section;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.session.local.LedgerMetering;
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
        MemoryAssembly.Wired wired = InMemoryAssembly.memory();
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
        MemoryAssembly.Wired wired = InMemoryAssembly.memory();
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

    @Test
    void budgetGateBlocksSecondTurnWithoutClaim() {
        MemoryAssembly.Wired wired = InMemoryAssembly.memory(
                new FakeLlmTransport(), LedgerMetering.tokens(2));
        Principal owner = Principal.personal(new PrincipalId("budget-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("budget-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        wired.bus().setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());

        session.inbox().enqueue("first", Optional.of("user"));
        TurnOutcome first = wired.loop().run(ctx, LoopConfig.of("fake-model"));
        assertTrue(first.completed(), first.detail());
        assertEquals(1, session.ledger().readAll().size());
        assertEquals(2, session.ledger().readAll().get(0).usage().totalTokens());

        session.inbox().enqueue("second", Optional.of("user"));
        int logSize = session.log().readAll().size();
        TurnOutcome second = wired.loop().run(ctx, LoopConfig.of("fake-model"));
        assertEquals(TurnOutcome.Kind.STOPPED, second.kind());
        assertTrue(second.detail().contains("BUDGET"), second.detail());
        assertEquals(logSize, session.log().readAll().size());
        assertTrue(session.inbox().claimNext().isPresent());
    }

    @Test
    void helpCommandDoesNotGenerate() {
        MemoryAssembly.Wired wired = InMemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("help-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("help-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());

        CommandResult r = wired.commands().dispatch(ctx, session, "/help");
        assertTrue(r.ok(), r.output());
        assertEquals(CommandKind.LOCAL, r.kind());
        assertTrue(r.output().contains("/help"), r.output());
        assertTrue(session.inbox().claimNext().isEmpty());
        assertTrue(session.log().readAll().isEmpty());
        assertTrue(session.ledger().readAll().isEmpty());
    }

    @Test
    void assembledSystemForwardsToLoop() {
        MemoryAssembly.Wired wired = InMemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("prompt-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("prompt-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        wired.bus().setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        wired.prompts().register(Section.stat("base", "identity-prefix"));
        AssembledPrompt assembled = wired.prompts().assemble(100);
        session.inbox().enqueue("hi", Optional.of("user"));

        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        LoopConfig config = new LoopConfig("fake-model", "anthropic", assembled.system(), LoopConfig.DEFAULT_MAX_STEPS);
        TurnOutcome o = wired.loop().run(ctx, config);
        assertTrue(o.completed(), o.detail());
        assertEquals("identity-prefix", session.ledger().readAll().get(0).attrs().get("system"));
    }

    @Test
    void auditSinkDoesNotWriteSessionLog() {
        MemoryAssembly.Wired wired = InMemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("audit-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("audit-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        wired.audit().record(owner.id().value(), "rest.invoke", "echo", Map.of("via", "bypass"));
        assertEquals(1, wired.audit().readAll().size());
        assertTrue(session.log().readAll().isEmpty());
        assertTrue(session.ledger().readAll().isEmpty());
    }
}
