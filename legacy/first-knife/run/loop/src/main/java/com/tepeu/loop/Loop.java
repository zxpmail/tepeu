package com.tepeu.loop;

import com.tepeu.dispatch.Dispatch;
import com.tepeu.identity.InvokeContext;
import com.tepeu.session.ClaimLease;
import com.tepeu.session.InboxMessage;
import com.tepeu.session.Session;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionLog;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 控制循环。领取 → 记 USER_MESSAGE → invoke {@link #LLM_GENERATE} →
 * 首行 {@code @tool} 则 TOOL_CALL + invoke 工具 + TOOL_RESULT → 回炉，至多 {@link #MAX_TOOL_ROUNDS}
 * 轮 → 终答记 ASSISTANT_MESSAGE。
 * 被拒或失败的调用（含 llm.generate 自身）记 TOOL_RESULT + attrs.error 短码（rewrite-0 §3.2）。
 * 寄存器 {@code loop.state}；用量进流水。完成判定 {@link #complete} 只读事件日志，单一判定点。
 */
public final class Loop {

    public static final String LLM_GENERATE = "llm.generate";
    public static final int MAX_TOOL_ROUNDS = 8;
    private static final String STATE_KEY = "loop.state";

    private final Dispatch dispatch;

    public Loop(Dispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    /** 领取一条输入跑到终答。队列空返回 false。 */
    public boolean runOnce(Session session) {
        Objects.requireNonNull(session, "session");
        Optional<ClaimLease> lease = session.inbox().claimNext();
        if (lease.isEmpty()) {
            return false;
        }
        session.registers().put(STATE_KEY, "running");
        try {
            InboxMessage message = session.inbox().claimed(lease.get().claimId()).orElseThrow();
            turn(session, message.body());
            session.inbox().ack(lease.get().claimId());
        } finally {
            session.registers().put(STATE_KEY, "idle");
        }
        return true;
    }

    /** 完成判定。只读事件日志：最后一条是 ASSISTANT_MESSAGE 即终答已落。 */
    public static boolean complete(SessionLog log) {
        Objects.requireNonNull(log, "log");
        List<SessionEvent> all = log.readAll();
        return !all.isEmpty()
                && all.get(all.size() - 1).type() == SessionEventType.ASSISTANT_MESSAGE;
    }

    private void turn(Session session, String input) {
        InvokeContext ctx = new InvokeContext(session.owner(), session.workspace(), session.id());
        session.log().append(SessionEventType.USER_MESSAGE, input);
        int rounds = 0;
        while (true) {
            SyscallResult gen = dispatch.invoke(ctx, LLM_GENERATE, Map.of());
            recordUsage(session, LLM_GENERATE, gen);
            if (!gen.ok()) {
                invokeFailed(session, LLM_GENERATE, gen);
                return;
            }
            Optional<ToolRequest> request = ToolRequest.parse(gen.output());
            if (request.isEmpty()) {
                session.log().append(SessionEventType.ASSISTANT_MESSAGE, gen.output());
                return;
            }
            if (rounds >= MAX_TOOL_ROUNDS) {
                session.log().append(SessionEventType.REASONING, "tool round limit reached");
                return;
            }
            rounds++;
            ToolRequest tool = request.get();
            session.log().append(SessionEventType.TOOL_CALL, tool.line(), Map.of("name", tool.name()));
            SyscallResult result = dispatch.invoke(ctx, tool.name(), tool.args());
            recordUsage(session, tool.name(), result);
            Map<String, String> attrs = result.ok()
                    ? Map.of("name", tool.name())
                    : Map.of("name", tool.name(), "error", result.errorCode().orElse("UNKNOWN"));
            session.log().append(SessionEventType.TOOL_RESULT, result.output(), attrs);
        }
    }

    private void recordUsage(Session session, String name, SyscallResult result) {
        Optional<Usage> usage = result.usage();
        usage.ifPresent(u -> session.ledger().record(name, u));
    }

    private void invokeFailed(Session session, String name, SyscallResult result) {
        session.log().append(SessionEventType.TOOL_RESULT, result.output(),
                Map.of("name", name, "error", result.errorCode().orElse("UNKNOWN")));
    }
}
