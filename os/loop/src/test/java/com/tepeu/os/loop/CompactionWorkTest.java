package com.tepeu.os.loop;

import com.tepeu.os.loop.local.CompactionWork;
import com.tepeu.os.bus.local.LocalCapabilityBus;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.memory.InMemorySessionStore;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactionWorkTest {

    @Test
    void maintainCompactsLiveSurfaceKeepsAuditAndStops() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("c-user")),
                Namespace.ofWorkspace(new WorkspaceId("c-ws")),
                Optional.empty());
        LocalCapabilityBus bus = new LocalCapabilityBus();
        bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        bus.setApprovalStore(new InMemoryApprovalStore());
        AtomicInteger llmCalls = new AtomicInteger();
        bus.register(SessionLoop.SYSCALL_GENERATE, (ctx, call) -> {
            llmCalls.incrementAndGet();
            assertEquals("compaction", call.args().get("system"));
            return SyscallResult.success("sum");
        });
        for (int i = 0; i < 6; i++) {
            session.log().append(SessionEventType.USER_MESSAGE, "m" + i, Map.of());
        }
        SessionLoop loop = new SessionLoop(store, bus);
        TurnContext turn = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        TurnOutcome o = loop.maintain(turn, MaintenanceConfig.of(Duration.ofSeconds(5)),
                new CompactionWork(bus, "fake-model", "anthropic", 2));
        assertTrue(o.detail().contains("MAINTENANCE_DONE"), o.detail());
        assertEquals(7, session.log().readAll().size());
        assertTrue(session.log().readAll().stream()
                .anyMatch(e -> e.type() == SessionEventType.COMPACTION_CHECKPOINT));
        assertEquals(1, llmCalls.get());
        assertEquals(3, session.logReplace().surface().size());
        assertEquals(SessionEventType.COMPACTION_CHECKPOINT, session.logReplace().surface().get(0).type());
        assertEquals(6, session.log().readAll().stream()
                .filter(e -> e.type() == SessionEventType.USER_MESSAGE).count());
    }

    @Test
    void doesNotCompactSeedZone() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session src = store.create(
                Principal.personal(new PrincipalId("c-user")),
                Namespace.ofWorkspace(new WorkspaceId("c-ws")),
                Optional.empty());
        src.log().append(SessionEventType.USER_MESSAGE, "seed-a", Map.of());
        src.log().append(SessionEventType.USER_MESSAGE, "seed-b", Map.of());
        Session child = store.fork(src.id(), 2);
        child.log().append(SessionEventType.USER_MESSAGE, "live-1", Map.of());
        child.log().append(SessionEventType.USER_MESSAGE, "live-2", Map.of());
        child.log().append(SessionEventType.USER_MESSAGE, "live-3", Map.of());
        LocalCapabilityBus bus = new LocalCapabilityBus();
        bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        bus.setApprovalStore(new InMemoryApprovalStore());
        bus.register(SessionLoop.SYSCALL_GENERATE, (ctx, call) -> SyscallResult.success("sum"));
        SessionLoop loop = new SessionLoop(store, bus);
        TurnContext turn = new TurnContext(child.owner(), child.namespace(), child.id(), Optional.empty());
        loop.maintain(turn, MaintenanceConfig.of(Duration.ofSeconds(5)),
                new CompactionWork(bus, "fake-model", "anthropic", 1));
        assertTrue(child.log().readAll().stream()
                .anyMatch(e -> "seed-a".equals(e.body())));
        assertTrue(child.logReplace().surface().stream()
                .anyMatch(e -> "seed-a".equals(e.body()) || "seed-b".equals(e.body())));
        assertTrue(child.logReplace().surface().stream()
                .noneMatch(e -> e.type() == SessionEventType.END_SEED));
    }

    @Test
    void alreadySmallSurfaceStopsWithoutLlm() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("c-user")),
                Namespace.ofWorkspace(new WorkspaceId("c-ws")),
                Optional.empty());
        session.log().append(SessionEventType.USER_MESSAGE, "only", Map.of());
        LocalCapabilityBus bus = new LocalCapabilityBus();
        bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        bus.setApprovalStore(new InMemoryApprovalStore());
        AtomicInteger llmCalls = new AtomicInteger();
        bus.register(SessionLoop.SYSCALL_GENERATE, (ctx, call) -> {
            llmCalls.incrementAndGet();
            return SyscallResult.success("sum");
        });
        SessionLoop loop = new SessionLoop(store, bus);
        TurnContext turn = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        TurnOutcome o = loop.maintain(turn, MaintenanceConfig.of(Duration.ofSeconds(5)),
                new CompactionWork(bus, "fake-model", "anthropic", 4));
        assertTrue(o.detail().contains("MAINTENANCE_DONE"), o.detail());
        assertEquals(0, llmCalls.get());
        assertEquals(1, session.logReplace().surface().size());
    }
}
