package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmGenerateHandler;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemorySessionStore;

/**
 * 单机内存接线 — compose 只组装，不承载业务。
 * Policy 仍须调用方装配（未装配即 fail-closed，不在此偷偷 ALLOW）。
 */
public final class MemoryAssembly {

    public record Wired(SessionStore sessions, CapabilityBus bus, ApprovalStore approvals, SessionLoop loop) {
    }

    private MemoryAssembly() {
    }

    public static Wired memory() {
        InMemorySessionStore sessions = new InMemorySessionStore();
        InMemoryCapabilityBus bus = new InMemoryCapabilityBus();
        InMemoryApprovalStore approvals = new InMemoryApprovalStore();
        bus.setApprovalStore(approvals);
        bus.register(LlmGenerateHandler.NAME, new LlmGenerateHandler(sessions, new FakeLlmTransport()));
        SessionLoop loop = new SessionLoop(sessions, bus);
        return new Wired(sessions, bus, approvals, loop);
    }
}
