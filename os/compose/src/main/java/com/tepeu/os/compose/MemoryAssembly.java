package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmGenerateHandler;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.HelpCommand;
import com.tepeu.os.orchestration.PromptAssembly;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.LedgerMetering;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemoryAuditSink;
import com.tepeu.os.session.memory.InMemorySessionStore;

/**
 * 单机内存接线 — compose 只组装，不承载业务。仅 conformance / 单测。
 * 发行默认见 {@link SqliteAssembly}（SQLite WAL；禁 {@code InMemoryApprovalStore}）。
 * Policy 仍须调用方装配（未装配即 fail-closed，不在此偷偷 ALLOW）。
 * 默认 fake 传输、unlimited Metering；真 HTTP / 预算顶由调用方注入，不在此读密钥。
 */
public final class MemoryAssembly {

    public record Wired(
            SessionStore sessions,
            CapabilityBus bus,
            ApprovalStore approvals,
            SessionLoop loop,
            PromptAssembly prompts,
            CommandDispatcher commands,
            AuditSink audit) implements AutoCloseable {
        @Override
        public void close() {
            Exception first = null;
            if (sessions instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception e) {
                    first = e;
                }
            }
            if (approvals instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
            if (first instanceof RuntimeException re) {
                throw re;
            }
            if (first != null) {
                throw new IllegalStateException("close assembly", first);
            }
        }
    }

    private MemoryAssembly() {
    }

    public static Wired memory() {
        return memory(new FakeLlmTransport());
    }

    public static Wired memory(LlmTransport transport) {
        return memory(transport, LedgerMetering.unlimited());
    }

    public static Wired memory(LlmTransport transport, Metering metering) {
        InMemorySessionStore sessions = new InMemorySessionStore();
        InMemoryApprovalStore approvals = new InMemoryApprovalStore();
        return wire(sessions, approvals, new InMemoryAuditSink(), transport, metering);
    }

    static Wired wire(
            SessionStore sessions,
            ApprovalStore approvals,
            AuditSink audit,
            LlmTransport transport,
            Metering metering) {
        InMemoryCapabilityBus bus = new InMemoryCapabilityBus();
        bus.setApprovalStore(approvals);
        bus.register(LlmGenerateHandler.NAME, new LlmGenerateHandler(sessions, transport));
        SessionLoop loop = new SessionLoop(sessions, bus, metering);
        PromptAssembly prompts = new PromptAssembly();
        CommandDispatcher commands = new CommandDispatcher();
        commands.register(new HelpCommand(commands));
        return new Wired(sessions, bus, approvals, loop, prompts, commands, audit);
    }
}
