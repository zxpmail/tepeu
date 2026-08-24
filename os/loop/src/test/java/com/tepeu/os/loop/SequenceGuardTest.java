package com.tepeu.os.loop;

import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
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
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceGuardTest {

    @Test
    void readThenSpawnBlockedAfterToolCallLogged() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("seq-user")),
                Namespace.ofWorkspace(new WorkspaceId("seq-ws")),
                Optional.empty());
        InMemoryCapabilityBus bus = new InMemoryCapabilityBus();
        bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
        bus.setApprovalStore(new InMemoryApprovalStore());
        bus.addGuardHook(new SequenceGuardHook(store));
        AtomicInteger spawn = new AtomicInteger();
        bus.register("execution.proc.spawn", (ctx, call) -> {
            spawn.incrementAndGet();
            return SyscallResult.success("ok");
        });
        TurnContext ctx = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        session.log().append(SessionEventType.TOOL_CALL, "execution.fs.read", Map.of());
        session.log().append(SessionEventType.TOOL_CALL, "execution.proc.spawn", Map.of("path", "run.sh"));
        try {
            bus.invoke(ctx, new Syscall("execution.proc.spawn", Map.of("path", "run.sh")));
        } catch (Exception e) {
            assertTrue(String.valueOf(e.getMessage()).contains("guard deny"));
        }
        assertEquals(0, spawn.get());
    }
}
