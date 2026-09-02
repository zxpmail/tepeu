package com.tepeu.os.loop;

import com.tepeu.os.bus.BusGuardException;
import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.ApprovalRequiredException;
import com.tepeu.os.policy.PolicyDeniedException;
import com.tepeu.os.session.ClaimLease;
import com.tepeu.os.session.InboxMessage;
import com.tepeu.os.loop.local.CompactionWork;
import com.tepeu.os.session.local.LedgerMetering;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * 会话主路：必经 Inbox/claim；llm.* 与工具都走总线；完成须过证据门。
 * 对人说「这轮完了」只许本类，且必须先过 {@link CompletionGate}；工具自报不是终态。
 * 阻塞式 + 显式门。不 import 具体 Tool 类。
 * TOOL_CALL/TOOL_RESULT 由本组件写 entries，总线不自动落事件。
 * 工具：先落 TOOL_CALL 再 invoke；拦截失败合成 TOOL_RESULT。
 * 开 turn 前读 Metering.withinBudget；超限不 claim（Metering 只供数）。
 * maintenance 独占 idle 窗口（强制上限 / NOW 让位 / latch）。DoomLoop 第三刀 → 总线 NEED_APPROVAL。
 */
public final class SessionLoop {

    public static final String SYSCALL_GENERATE = "llm.generate";
    public static final String REGISTER_STATE = "loop.state";
    public static final String REGISTER_LATCH = "loop.latch";

    private static final Logger LOG = System.getLogger(SessionLoop.class.getName());
    private static final String COMPONENT = "loop";
    private static final String CLASS_NAME = SessionLoop.class.getSimpleName();

    private final SessionStore sessions;
    private final CapabilityBus bus;
    private final Metering metering;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public SessionLoop(SessionStore sessions, CapabilityBus bus) {
        this(sessions, bus, LedgerMetering.unlimited());
    }

    public SessionLoop(SessionStore sessions, CapabilityBus bus, Metering metering) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.metering = Objects.requireNonNull(metering, "metering");
    }

    public TurnOutcome run(TurnContext ctx, LoopConfig config) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(config, "config");
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return finish(ctx, TurnOutcome.invalid("session not found"));
        }
        synchronized (lockFor(session.id())) {
            LoopState current = readState(session);
            if (current != LoopState.IDLE) {
                return finish(ctx, TurnOutcome.invalid("loop.state=" + current));
            }
            if (config.maxSteps() < 1) {
                return finish(ctx, TurnOutcome.stopped(0, "maxSteps < 1"));
            }
            boolean within;
            try {
                within = metering.withinBudget(session);
            } catch (RuntimeException e) {
                return finish(ctx, TurnOutcome.failed(0,
                        "metering failed (fail-closed): " + String.valueOf(e.getMessage())));
            }
            if (!within) {
                return finish(ctx, TurnOutcome.stopped(0, "BUDGET"));
            }
            session.registers().put(REGISTER_STATE, LoopState.RUNNING.name());
            int steps = 0;
            try {
                Optional<ClaimLease> leaseOpt = session.inbox().claimNext();
                if (leaseOpt.isEmpty()) {
                    return finish(ctx, TurnOutcome.empty());
                }
                ClaimLease lease = leaseOpt.get();
                InboxMessage msg = session.inbox().claimed(lease.claimId()).orElse(null);
                if (msg == null) {
                    session.inbox().nack(lease.claimId());
                    return finish(ctx, TurnOutcome.failed(0, "claimed message missing"));
                }
                long userSeq;
                try {
                    userSeq = session.log().append(SessionEventType.USER_MESSAGE, msg.body(), attrs(msg));
                    session.inbox().ack(lease.claimId());
                } catch (RuntimeException e) {
                    session.inbox().nack(lease.claimId());
                    return finish(ctx, TurnOutcome.failed(0, String.valueOf(e.getMessage())));
                }
                boolean wantsContinue = true;
                while (wantsContinue && steps < config.maxSteps()) {
                    steps++;
                    try {
                        maybeCompactOverflow(session, ctx, config);
                    } catch (RuntimeException e) {
                        return finish(ctx, TurnOutcome.failed(steps,
                                "overflow compact: " + String.valueOf(e.getMessage())));
                    }
                    SyscallResult result;
                    try {
                        result = bus.invoke(ctx, new Syscall(SYSCALL_GENERATE, generateArgs(config)));
                    } catch (BusGuardException | PolicyDeniedException | ApprovalRequiredException e) {
                        return finish(ctx, TurnOutcome.failed(steps, String.valueOf(e.getMessage())));
                    }
                    if (!result.ok()) {
                        return finish(ctx, TurnOutcome.failed(steps,
                                result.errorCode().orElse("FAILED")));
                    }
                    Optional<ToolDirective> tool = ToolDirective.parse(result.output());
                    if (tool.isPresent()) {
                        TurnOutcome halt = invokeTool(session, ctx, tool.get(), steps);
                        if (halt != null) {
                            return finish(ctx, halt);
                        }
                        continue;
                    }
                    Optional<PlanDirective> plan = PlanDirective.parse(result.output());
                    if (plan.isPresent()) {
                        session.log().append(SessionEventType.PLAN_STEP, plan.get().body(), plan.get().attrs());
                        continue;
                    }
                    if (result.output().isBlank()) {
                        return finish(ctx, TurnOutcome.incomplete(steps,
                                "reply requires non-blank ASSISTANT_MESSAGE"));
                    }
                    session.log().append(SessionEventType.ASSISTANT_MESSAGE, result.output(), Map.of());
                    wantsContinue = false;
                }
                if (wantsContinue) {
                    return finish(ctx, TurnOutcome.stopped(steps, "maxSteps reached"));
                }
                for (CompletionClaim claim : CompletionGate.infer(session, userSeq)) {
                    Optional<String> refuse = CompletionGate.refuseReason(session, claim, userSeq);
                    if (refuse.isPresent()) {
                        return finish(ctx, TurnOutcome.incomplete(steps, refuse.get()));
                    }
                }
                return finish(ctx, TurnOutcome.completed(steps));
            } finally {
                session.registers().put(REGISTER_STATE, LoopState.IDLE.name());
            }
        }
    }

    /**
     * 独占 maintenance 窗口。不 claim。工作须协作让出。
     * COMPLETED 不用在这条路上（完成仍是答复证据门）。
     */
    public TurnOutcome maintain(TurnContext ctx, MaintenanceConfig config, MaintenanceWork work) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(work, "work");
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return finish(ctx, TurnOutcome.invalid("session not found"));
        }
        synchronized (lockFor(session.id())) {
            LoopState current = readState(session);
            if (current != LoopState.IDLE) {
                return finish(ctx, TurnOutcome.invalid("loop.state=" + current));
            }
            if (session.inbox().hasClaimableNow()) {
                return finish(ctx, TurnOutcome.stopped(0, "MAINTENANCE_NOW"));
            }
            boolean within;
            try {
                within = config.windowMetering().withinBudget(session);
            } catch (RuntimeException e) {
                return finish(ctx, TurnOutcome.failed(0,
                        "metering failed (fail-closed): " + String.valueOf(e.getMessage())));
            }
            if (!within) {
                return finish(ctx, TurnOutcome.stopped(0, "BUDGET"));
            }
            long latch = session.inbox().enqueued();
            session.registers().put(REGISTER_LATCH, Long.toString(latch));
            session.registers().put(REGISTER_STATE, LoopState.MAINTENANCE.name());
            int steps = 0;
            try {
                Instant deadline = config.clock().instant().plus(config.maxWindow());
                while (true) {
                    if (!config.clock().instant().isBefore(deadline)) {
                        return finish(ctx, TurnOutcome.stopped(steps, "MAINTENANCE_LIMIT"));
                    }
                    if (steps > 0 && session.inbox().hasClaimableNow()) {
                        return finish(ctx, TurnOutcome.stopped(steps, "MAINTENANCE_NOW"));
                    }
                    boolean more;
                    try {
                        more = work.step(session, ctx);
                    } catch (RuntimeException e) {
                        return finish(ctx, TurnOutcome.failed(steps, String.valueOf(e.getMessage())));
                    }
                    steps++;
                    if (!more) {
                        return finish(ctx, TurnOutcome.stopped(steps, "MAINTENANCE_DONE"));
                    }
                }
            } finally {
                session.registers().put(REGISTER_STATE, LoopState.IDLE.name());
            }
        }
    }

    /**
     * @return 非 null 则中止本轮；null 表示已成对落账，继续 generate。
     */
    private TurnOutcome invokeTool(Session session, TurnContext ctx, ToolDirective tool, int steps) {
        session.log().append(SessionEventType.TOOL_CALL, tool.name(), tool.args());
        if (SYSCALL_GENERATE.equals(tool.name())) {
            session.log().append(SessionEventType.TOOL_RESULT,
                    "loop will not invoke llm.generate as a tool",
                    interceptAttrs("STRUCTURAL"));
            return TurnOutcome.failed(steps, "cannot invoke llm.generate as a tool");
        }
        try {
            SyscallResult toolResult = bus.invoke(ctx, new Syscall(tool.name(), tool.args()));
            session.log().append(SessionEventType.TOOL_RESULT, toolResult.output(),
                    resultAttrs(session, tool, toolResult));
            return null;
        } catch (BusGuardException | PolicyDeniedException | ApprovalRequiredException e) {
            session.log().append(SessionEventType.TOOL_RESULT, String.valueOf(e.getMessage()),
                    interceptAttrs(interceptCode(e)));
            return TurnOutcome.failed(steps, String.valueOf(e.getMessage()));
        }
    }

    private Object lockFor(SessionId id) {
        return locks.computeIfAbsent(id.value(), k -> new Object());
    }

    private void maybeCompactOverflow(Session session, TurnContext ctx, LoopConfig config) {
        if (config.compactOverflow() < 1) {
            return;
        }
        if (CompactionWork.liveSurface(session).size() <= config.compactOverflow()) {
            return;
        }
        new CompactionWork(bus, config.model(), config.family(), config.compactKeepLast())
                .step(session, ctx);
    }

    /** 引擎层一轮结局。不打 inbox / 模型正文。host 另记 CLI 面。 */
    private static TurnOutcome finish(TurnContext ctx, TurnOutcome outcome) {
        Level level = switch (outcome.kind()) {
            case FAILED, INVALID -> Level.WARNING;
            default -> Level.INFO;
        };
        LOG.log(level, "component={0} class={1} session={2} kind={3} steps={4} reason={5}",
                COMPONENT, CLASS_NAME, ctx.sessionId().value(),
                outcome.kind(), String.valueOf(outcome.steps()), outcome.detail());
        return outcome;
    }

    static final String FS_WRITE = "execution.fs.write";

    private static Map<String, String> resultAttrs(Session session, ToolDirective tool, SyscallResult result) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("ok", Boolean.toString(result.ok()));
        result.errorCode().ifPresent(code -> attrs.put("errorCode", code));
        if (result.ok() && FS_WRITE.equals(tool.name())) {
            byte[] bytes = tool.args().getOrDefault("content", "").getBytes(StandardCharsets.UTF_8);
            attrs.put("locator", session.blobs().put(bytes));
        }
        return attrs;
    }

    private static Map<String, String> interceptAttrs(String errorCode) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("ok", "false");
        attrs.put("errorCode", errorCode);
        return attrs;
    }

    private static String interceptCode(RuntimeException e) {
        if (e instanceof BusGuardException) {
            return "GUARD";
        }
        if (e instanceof ApprovalRequiredException) {
            return "APPROVAL";
        }
        return "POLICY";
    }

    private static LoopState readState(Session session) {
        Optional<String> raw = session.registers().get(REGISTER_STATE);
        if (raw.isEmpty() || raw.get().isBlank()) {
            return LoopState.IDLE;
        }
        try {
            return LoopState.valueOf(raw.get());
        } catch (IllegalArgumentException e) {
            return LoopState.RUNNING;
        }
    }

    private static Map<String, String> attrs(InboxMessage msg) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("messageId", msg.id());
        msg.source().ifPresent(s -> attrs.put("source", s));
        return attrs;
    }

    private static Map<String, String> generateArgs(LoopConfig config) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("model", config.model());
        args.put("family", config.family());
        if (!config.system().isEmpty()) {
            args.put("system", config.system());
        }
        return args;
    }
}
