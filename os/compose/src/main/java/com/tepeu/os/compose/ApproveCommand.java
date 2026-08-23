package com.tepeu.os.compose;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.orchestration.CommandHandler;
import com.tepeu.os.orchestration.CommandKind;
import com.tepeu.os.orchestration.CommandResult;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.session.AuditSink;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** /approve &lt;id&gt; allow|deny — local，写 AuditSink，不进会话事件。 */
public final class ApproveCommand implements CommandHandler {

    private final ApprovalStore approvals;
    private final AuditSink audit;

    public ApproveCommand(ApprovalStore approvals, AuditSink audit) {
        this.approvals = Objects.requireNonNull(approvals, "approvals");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public CommandKind kind() {
        return CommandKind.LOCAL;
    }

    @Override
    public String name() {
        return "approve";
    }

    @Override
    public String description() {
        return "<id> allow|deny";
    }

    @Override
    public CommandResult execute(TurnContext ctx, List<String> args) {
        if (args == null || args.size() < 2) {
            return CommandResult.failure("usage: /approve <id> allow|deny");
        }
        String id = args.get(0);
        String verb = args.get(1).toLowerCase(Locale.ROOT);
        boolean allow;
        if ("allow".equals(verb)) {
            allow = true;
        } else if ("deny".equals(verb)) {
            allow = false;
        } else {
            return CommandResult.failure("usage: /approve <id> allow|deny");
        }
        String actor = ctx.principal().id().value();
        var record = approvals.get(id);
        if (record.isEmpty()) {
            return CommandResult.failure("unknown approval: " + id);
        }
        if (!record.get().sessionId().equals(ctx.sessionId().value())) {
            return CommandResult.failure("approval not in this session");
        }
        try {
            approvals.decide(id, allow, actor);
        } catch (RuntimeException e) {
            return CommandResult.failure(String.valueOf(e.getMessage()));
        }
        audit.record(actor, "approve", id, Map.of("allow", Boolean.toString(allow)));
        return CommandResult.local(allow ? "allowed " + id : "denied " + id);
    }
}
