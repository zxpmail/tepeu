package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.execution.ExecutionNames;
import com.tepeu.os.execution.FsReadHandler;
import com.tepeu.os.execution.FsWriteHandler;
import com.tepeu.os.execution.ProcSpawnHandler;
import com.tepeu.os.execution.SandboxPolicy;
import com.tepeu.os.execution.SandboxProbeHandler;
import com.tepeu.os.llm.LlmGenerateHandler;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.loop.DoomLoopGuardHook;
import com.tepeu.os.loop.SequenceGuardHook;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.observation.ContextShapers;
import com.tepeu.os.observation.Observation;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.EmptyKnowledgeSource;
import com.tepeu.os.orchestration.HelpCommand;
import com.tepeu.os.orchestration.KnowledgeSource;
import com.tepeu.os.orchestration.PromptAssembly;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.DefaultRuleMatrix;
import com.tepeu.os.policy.PolicyHook;
import com.tepeu.os.policy.PolicyRulesFile;
import com.tepeu.os.policy.persist.ApprovalPersistence;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.persist.SessionPersistence;
import com.tepeu.os.session.LocalProjectionBus;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.ProjectionBus;
import com.tepeu.os.persist.Persist;
import com.tepeu.os.session.SessionStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 开机接线 — compose 只组装，不承载业务。
 * 发行入口 {@link SqliteAssembly}。内存内核工厂在测试源，不进本类。
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
            /** 投影端口；本骨架默认 {@link LocalProjectionBus}，不是契约。MQ 升版可换。 */
            ProjectionBus projection,
            KnowledgeSource knowledge,
            /** 发行路径由调用方注入 Persist；内存夹具为 null，close 时关内存 store。 */
            Persist kernelDb,
            Persist approvalsDb) implements AutoCloseable {
        @Override
        public void close() {
            Exception first = null;
            if (kernelDb != null || approvalsDb != null) {
                first = closeQuiet(kernelDb, null);
                first = closeQuiet(approvalsDb, first);
            } else {
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
            }
            if (first instanceof RuntimeException re) {
                throw re;
            }
            if (first != null) {
                throw new IllegalStateException("close assembly", first);
            }
        }

        private static Exception closeQuiet(AutoCloseable c, Exception first) {
            if (c == null) {
                return first;
            }
            try {
                c.close();
                return first;
            } catch (Exception e) {
                return first == null ? e : first;
            }
        }
    }

    private MemoryAssembly() {
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
        return wire(sessions, approvals, audit, transport, metering, workspace, policy, null, null);
    }

    static Wired wire(
            Persist kernelDb,
            Persist approvalsDb,
            LlmTransport transport,
            Metering metering,
            Path workspace,
            PolicyHook policy) {
        SessionPersistence session = SessionPersistence.open(kernelDb);
        ApprovalStore approvals = ApprovalPersistence.open(approvalsDb);
        return wire(session.sessions(), approvals, session.audit(), transport, metering, workspace,
                policy, kernelDb, approvalsDb);
    }

    static Wired wire(
            SessionStore sessions,
            ApprovalStore approvals,
            AuditSink audit,
            LlmTransport transport,
            Metering metering,
            Path workspace,
            PolicyHook policy,
            Persist kernelDb,
            Persist approvalsDb) {
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
        Observation.install(ContextShapers.defaults());
        SessionLoop loop = new SessionLoop(sessions, bus, metering);
        PromptAssembly prompts = new PromptAssembly();
        CommandDispatcher commands = new CommandDispatcher();
        commands.register(new HelpCommand(commands));
        commands.register(new ApproveCommand(approvals, audit));
        // 本骨架默认实现；业务只认 ProjectionBus 接口。MQ/Redis 升版再换，此处不引入 broker。
        ProjectionBus projection = new LocalProjectionBus();
        KnowledgeSource knowledge = new EmptyKnowledgeSource();
        return new Wired(sessions, bus, approvals, loop, prompts, commands, audit, workspace, projection,
                knowledge, kernelDb, approvalsDb);
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
