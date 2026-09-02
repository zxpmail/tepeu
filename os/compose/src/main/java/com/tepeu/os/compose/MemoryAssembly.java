package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.local.LocalCapabilityBus;
import com.tepeu.os.execution.ExecutionNames;
import com.tepeu.os.execution.local.FsReadHandler;
import com.tepeu.os.execution.local.FsWriteHandler;
import com.tepeu.os.execution.local.OsJails;
import com.tepeu.os.execution.local.ProcSpawnHandler;
import com.tepeu.os.execution.SandboxPolicy;
import com.tepeu.os.execution.local.SandboxProbeHandler;
import com.tepeu.os.llm.local.LlmGenerateHandler;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.loop.local.DoomLoopGuardHook;
import com.tepeu.os.loop.local.SequenceGuardHook;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.observation.local.ContextShapers;
import com.tepeu.os.observation.Observation;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.local.EmptyKnowledgeSource;
import com.tepeu.os.orchestration.local.HelpCommand;
import com.tepeu.os.orchestration.KnowledgeSource;
import com.tepeu.os.orchestration.local.PromptAssembly;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.local.DefaultRuleMatrix;
import com.tepeu.os.policy.PolicyHook;
import com.tepeu.os.policy.local.PolicyRulesFile;
import com.tepeu.os.policy.persist.ApprovalPersistence;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.persist.SessionPersistence;
import com.tepeu.os.session.local.LocalProjectionBus;
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
            /** 投影端口。业务只认接口；本机默认插头另接。 */
            ProjectionBus projection,
            KnowledgeSource knowledge,
            /** 发行路径由调用方注入 Persist；本记录不关库。内存夹具为 null，close 时关内存 store。 */
            Persist kernelDb,
            Persist approvalsDb) implements AutoCloseable {
        @Override
        public void close() {
            if (kernelDb != null || approvalsDb != null) {
                return;
            }
            Exception first = closeMemory(sessions, null);
            first = closeMemory(approvals, first);
            if (first instanceof RuntimeException re) {
                throw re;
            }
            if (first != null) {
                throw new IllegalStateException("close assembly", first);
            }
        }

        private static Exception closeMemory(Object store, Exception first) {
            if (!(store instanceof AutoCloseable c)) {
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
        LocalCapabilityBus bus = new LocalCapabilityBus();
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
        // 本机默认插头。升版换 MQ 时由此注入或改这一行，不要改 LocalProjectionBus。不引 broker。
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

    private static void registerExecution(LocalCapabilityBus bus, Path workspace) {
        SandboxPolicy sandbox = new SandboxPolicy(workspace, OsJails.detect());
        bus.register(ExecutionNames.FS_READ, new FsReadHandler(sandbox));
        bus.register(ExecutionNames.FS_WRITE, new FsWriteHandler(sandbox));
        bus.register(ExecutionNames.PROC_SPAWN, new ProcSpawnHandler(sandbox));
        bus.register(ExecutionNames.SANDBOX_PROBE, new SandboxProbeHandler(sandbox));
    }
}
