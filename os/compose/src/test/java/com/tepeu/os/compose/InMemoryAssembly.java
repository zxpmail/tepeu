package com.tepeu.os.compose;

import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.LedgerMetering;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.memory.InMemoryAuditSink;
import com.tepeu.os.session.memory.InMemorySessionStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 测试内核工厂 — 内存 SessionStore / ApprovalStore。不进发行 jar。
 * 发行入口 {@link SqliteAssembly}。
 */
final class InMemoryAssembly {

    private InMemoryAssembly() {
    }

    static MemoryAssembly.Wired memory() {
        return memory(new FakeLlmTransport());
    }

    static MemoryAssembly.Wired memory(LlmTransport transport) {
        return memory(transport, LedgerMetering.unlimited());
    }

    static MemoryAssembly.Wired memory(LlmTransport transport, Metering metering) {
        InMemorySessionStore sessions = new InMemorySessionStore();
        InMemoryApprovalStore approvals = new InMemoryApprovalStore();
        try {
            Path workspace = Files.createTempDirectory("tepeu-mem-ws-");
            return MemoryAssembly.wire(sessions, approvals, new InMemoryAuditSink(), transport, metering, workspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
