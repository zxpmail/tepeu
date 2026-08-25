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
import com.tepeu.os.session.SessionProjections;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public final class CliSession {

    private final MemoryAssembly.Wired kernel;
    private final Session session;
    private final TurnContext turnContext;
    private final LoopConfig loopConfig;
    private long projectionCursor;

    CliSession(MemoryAssembly.Wired kernel, TepeuHostProperties props, LoopConfig loopConfig) {
        this.kernel = kernel;
        this.loopConfig = loopConfig;
        Principal owner = Principal.personal(new PrincipalId(props.principalId()));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId(props.workspaceId()));
        this.session = kernel.sessions().create(owner, ns, Optional.empty());
        this.turnContext = new TurnContext(owner, ns, session.id(), Optional.empty());
    }

    SessionId sessionId() {
        return session.id();
    }

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
        CommandResult result = kernel.commands().dispatch(turnContext, session, line);
        if (!result.ok()) {
            System.out.println(result.output());
            return TurnOutcome.failed(0, result.output());
        }
        if (result.kind() == CommandKind.LOCAL) {
            if (!result.output().isBlank()) {
                System.out.println(result.output());
            }
            return TurnOutcome.completed(0);
        }
        return runLoop();
    }

    private TurnOutcome runLoop() {
        TurnOutcome outcome = kernel.loop().run(turnContext, loopConfig);
        flushProjection();
        printOutcomeSummary(outcome);
        return outcome;
    }

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
                default -> { /* USER_MESSAGE etc. omitted in CLI */ }
            }
        }
    }

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

    boolean isExit(TurnOutcome outcome) {
        return outcome != null && outcome.kind() == TurnOutcome.Kind.STOPPED
                && "cli exit".equals(outcome.detail());
    }
}
