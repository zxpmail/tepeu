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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
