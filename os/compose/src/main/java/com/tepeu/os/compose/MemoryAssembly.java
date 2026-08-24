package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.execution.ExecutionNames;
import com.tepeu.os.execution.FsReadHandler;
import com.tepeu.os.execution.FsWriteHandler;
import com.tepeu.os.execution.ProcSpawnHandler;
import com.tepeu.os.execution.SandboxPolicy;
import com.tepeu.os.execution.SandboxProbeHandler;
import com.tepeu.os.llm.ContextShapers;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.ModelContext;
import com.tepeu.os.llm.LlmGenerateHandler;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.loop.DoomLoopGuardHook;
import com.tepeu.os.loop.SequenceGuardHook;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.EmptyKnowledgeSource;
import com.tepeu.os.orchestration.HelpCommand;
import com.tepeu.os.orchestration.KnowledgeSource;
import com.tepeu.os.orchestration.PromptAssembly;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.DefaultRuleMatrix;
import com.tepeu.os.policy.PolicyHook;
import com.tepeu.os.policy.PolicyRulesFile;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.LedgerMetering;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.ProjectionBus;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemoryAuditSink;
import com.tepeu.os.session.memory.InMemoryProjectionBus;
import com.tepeu.os.session.memory.InMemorySessionStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 单机内存接线 — compose 只组装，不承载业务。仅 conformance / 单测。
 * 发行默认见 {@link SqliteAssembly}。
 * 默认装 {@link DefaultRuleMatrix}（不是 ALLOW-all）；裸总线未装配仍 C2。
 * execution.* 工作区囚笼，隔离报 partial。llm 默认 fake。
 */
public final class MemoryAssembly {

    public record Wired(
            SessionStore sessions,
            CapabilityBus bus,
            ApprovalStore approvals,
            SessionLoop loop,
            PromptAssembly prompts,
            CommandDispatcher commands,
            AuditSink audit,
            Path workspace,
            ProjectionBus projection,
            KnowledgeSource knowledge) implements AutoCloseable {
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
        try {
            Path workspace = Files.createTempDirectory("tepeu-mem-ws-");
            return wire(sessions, approvals, new InMemoryAuditSink(), transport, metering, workspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Wired wire(
            SessionStore sessions,
            ApprovalStore approvals,
            AuditSink audit,
            LlmTransport transport,
            Metering metering,
            Path workspace) {
        return wire(sessions, approvals, audit, transport, metering, workspace, defaultPolicy());
    }

    static Wired wire(
            SessionStore sessions,
            ApprovalStore approvals,
            AuditSink audit,
            LlmTransport transport,
            Metering metering,
            Path workspace,
            PolicyHook policy) {
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        InMemoryCapabilityBus bus = new InMemoryCapabilityBus();
        bus.setApprovalStore(approvals);
        bus.setPolicyHook(policy);
        bus.addGuardHook(new DoomLoopGuardHook(sessions));
        bus.addGuardHook(new SequenceGuardHook(sessions));
        bus.register(LlmGenerateHandler.NAME, new LlmGenerateHandler(sessions, transport));
        registerExecution(bus, workspace);
        ModelContext.install(ContextShapers.defaults());
        SessionLoop loop = new SessionLoop(sessions, bus, metering);
        PromptAssembly prompts = new PromptAssembly();
        CommandDispatcher commands = new CommandDispatcher();
        commands.register(new HelpCommand(commands));
        commands.register(new ApproveCommand(approvals, audit));
        ProjectionBus projection = new InMemoryProjectionBus();
        KnowledgeSource knowledge = new EmptyKnowledgeSource();
        return new Wired(sessions, bus, approvals, loop, prompts, commands, audit, workspace, projection,
                knowledge);
    }

    /** 发行默认：名级矩阵 + 参数级 deny（内置清单）。 */
    public static PolicyHook defaultPolicy() {
        return PolicyRulesFile.builtins().composePolicy();
    }

    public static PolicyHook defaultPolicy(DefaultRuleMatrix matrix) {
        PolicyRulesFile base = PolicyRulesFile.builtins();
        return new PolicyRulesFile(matrix, base.denyPaths(), base.denyCommands()).composePolicy();
    }

    public static PolicyHook defaultPolicy(PolicyRulesFile rules) {
        return rules.composePolicy();
    }

    private static void registerExecution(InMemoryCapabilityBus bus, Path workspace) {
        SandboxPolicy sandbox = new SandboxPolicy(workspace);
        bus.register(ExecutionNames.FS_READ, new FsReadHandler(sandbox));
        bus.register(ExecutionNames.FS_WRITE, new FsWriteHandler(sandbox));
        bus.register(ExecutionNames.PROC_SPAWN, new ProcSpawnHandler(sandbox));
        bus.register(ExecutionNames.SANDBOX_PROBE, new SandboxProbeHandler(sandbox));
    }
}
