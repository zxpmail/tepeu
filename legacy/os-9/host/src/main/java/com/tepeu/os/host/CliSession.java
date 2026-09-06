package com.tepeu.os.host;

import com.tepeu.os.compose.MemoryAssembly;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.CommandKind;
import com.tepeu.os.orchestration.CommandResult;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.local.SessionProjections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 单会话 CLI 门面 — 一行输入分发给 Slash / meta / Inbox+Loop。
 * 每次进程启动新建 Session（尚无 resume）；完成证据仍由 Loop CompletionGate 判定。
 * CLI 打印与 local Slash 的 exit 0 不是 Agent 终态。
 */
@Component
public final class CliSession {

    private static final Logger log = LoggerFactory.getLogger(CliSession.class);

    private final MemoryAssembly.Wired kernel;
    private final Session session;
    private final TurnContext turnContext;
    private final LoopConfig loopConfig;
    /** ProjectionBus since 游标（与 entries seq 同型）。 */
    private long projectionCursor;

    CliSession(MemoryAssembly.Wired kernel, TepeuHostProperties props, LoopConfig loopConfig) {
        this.kernel = kernel;
        this.loopConfig = loopConfig;
        Principal owner = Principal.personal(new PrincipalId(props.principalId()));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId(props.workspaceId()));
        this.session = kernel.sessions().create(owner, ns, Optional.empty());
        this.turnContext = new TurnContext(owner, ns, session.id(), Optional.empty());
        log.info("component=host session={} surface=created", session.id().value());
    }

    SessionId sessionId() {
        return session.id();
    }

    /**
     * 处理一行输入。
     * <ul>
     *   <li>{@code :meta} — 宿主元命令（不经 Slash / Loop）</li>
     *   <li>{@code /slash} — CommandDispatcher（local 不跑 Loop；prompt 型入队后再跑）</li>
     *   <li>其余 — Inbox enqueue → SessionLoop.run</li>
     * </ul>
     *
     * @return 空行返回 null；成功 local Slash / :session 返回 COMPLETED（便于 exit 0）
     */
    TurnOutcome handleLine(String line) {
        String text = line == null ? "" : line.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith(":")) {
            return handleMeta(text.substring(1).strip());
        }
        if (CommandDispatcher.parse(text).isPresent()) {
            return handleSlash(text);
        }
        session.inbox().enqueue(text, Optional.empty());
        return runLoop();
    }

    private TurnOutcome handleSlash(String line) {
        String name = CommandDispatcher.parse(line).map(CommandDispatcher.Parsed::name).orElse("-");
        CommandResult result = kernel.commands().dispatch(turnContext, session, line);
        if (!result.ok()) {
            log.warn("component=host session={} surface=slash name={} ok=false",
                    session.id().value(), name);
            System.out.println(result.output());
            return TurnOutcome.failed(0, result.output());
        }
        if (result.kind() == CommandKind.LOCAL) {
            log.info("component=host session={} surface=slash name={} ok=true",
                    session.id().value(), name);
            if (!result.output().isBlank()) {
                System.out.println(result.output());
            }
            return TurnOutcome.completed(0);
        }
        // PROMPT 型：dispatch 已 enqueue，再跑 Loop
        return runLoop();
    }

    private TurnOutcome runLoop() {
        TurnOutcome outcome = kernel.loop().run(turnContext, loopConfig);
        log.info("component=host session={} surface=loop kind={} steps={}",
                session.id().value(), outcome.kind(), outcome.steps());
        flushProjection();
        printOutcomeSummary(outcome);
        return outcome;
    }

    /** {@code :session} / {@code :quit}；未知 meta 记 FAILED。 */
    private TurnOutcome handleMeta(String meta) {
        return switch (meta.toLowerCase()) {
            case "session" -> {
                System.out.println("session=" + session.id().value());
                yield TurnOutcome.completed(0);
            }
            case "quit", "exit" -> TurnOutcome.stopped(0, "cli exit");
            default -> {
                System.out.println("unknown meta command; use :session :quit");
                yield TurnOutcome.failed(0, "unknown meta");
            }
        };
    }

    /** 增量读真相、推 ProjectionBus，并打印助手/工具摘要。 */
    private void flushProjection() {
        long before = projectionCursor;
        List<SessionEvent> fresh = SessionProjections.since(session, before);
        projectionCursor = SessionProjections.publishCatchUp(session, kernel.projection(), before);
        printEvents(fresh);
    }

    private void printEvents(List<SessionEvent> events) {
        for (SessionEvent event : events) {
            switch (event.type()) {
                case ASSISTANT_MESSAGE -> {
                    System.out.println();
                    System.out.println("assistant> " + event.body());
                }
                case TOOL_CALL -> System.out.println("[tool call] " + event.body());
                case TOOL_RESULT -> System.out.println("[tool result] " + truncate(event.body(), 200));
                default -> { /* USER_MESSAGE 等 CLI 省略 */ }
            }
        }
    }

    /** 非 COMPLETED/EMPTY 时打印结局，避免成功路径刷屏。 */
    private static void printOutcomeSummary(TurnOutcome outcome) {
        if (outcome.kind() != TurnOutcome.Kind.COMPLETED && outcome.kind() != TurnOutcome.Kind.EMPTY) {
            System.out.println("[" + outcome.kind() + "] " + outcome.detail());
        }
    }

    private static String truncate(String body, int max) {
        if (body == null) {
            return "";
        }
        if (body.length() <= max) {
            return body;
        }
        return body.substring(0, max) + "…";
    }

    /** REPL 退出约定：STOPPED + detail=cli exit。 */
    boolean isExit(TurnOutcome outcome) {
        return outcome != null && outcome.kind() == TurnOutcome.Kind.STOPPED
                && "cli exit".equals(outcome.detail());
    }
}
