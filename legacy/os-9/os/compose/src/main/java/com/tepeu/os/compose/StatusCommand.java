package com.tepeu.os.compose;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.orchestration.CommandHandler;
import com.tepeu.os.orchestration.CommandKind;
import com.tepeu.os.orchestration.CommandResult;
import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionStore;

import java.util.List;
import java.util.Objects;

/**
 * /status — 读本会话账本与未决审批。local，不经模型，不写 entries。
 */
public final class StatusCommand implements CommandHandler {

    private final SessionStore sessions;
    private final ApprovalStore approvals;

    public StatusCommand(SessionStore sessions, ApprovalStore approvals) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.approvals = Objects.requireNonNull(approvals, "approvals");
    }

    @Override
    public CommandKind kind() {
        return CommandKind.LOCAL;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "session log and pending asks";
    }

    @Override
    public CommandResult execute(TurnContext ctx, List<String> args) {
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return CommandResult.failure("session not found");
        }
        String state = session.registers().get("loop.state").orElse("IDLE");
        List<SessionEvent> events = session.log().readAll();
        List<ApprovalRecord> pending = approvals.records().stream()
                .filter(r -> r.sessionId().equals(ctx.sessionId().value()) && !r.decided())
                .toList();
        StringBuilder out = new StringBuilder();
        out.append("session=").append(session.id().value()).append('\n');
        out.append("loop.state=").append(state).append('\n');
        out.append("entries=").append(events.size());
        for (SessionEvent event : events) {
            out.append('\n').append(event.seq()).append(' ').append(event.type());
        }
        out.append('\n').append("pending=").append(pending.size());
        for (ApprovalRecord record : pending) {
            out.append('\n').append(record.approvalId()).append(' ').append(record.syscallName());
        }
        return CommandResult.local(out.toString());
    }
}
