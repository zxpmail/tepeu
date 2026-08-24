package com.tepeu.os.loop;

import com.tepeu.os.bus.GuardHook;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;

import java.util.Objects;

/**
 * 跨工具序列熔断卫兵 — 禁止后缀命中则 DENY（须在 Loop 落 TOOL_CALL 之后经总线 invoke）。
 */
public final class SequenceGuardHook implements GuardHook {

    private final SessionStore sessions;

    public SequenceGuardHook(SessionStore sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public PolicyVerdict before(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        if (SessionLoop.SYSCALL_GENERATE.equals(syscall.name())) {
            return PolicyVerdict.ALLOW;
        }
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return PolicyVerdict.ALLOW;
        }
        if (ToolSequence.tripped(session, syscall.name())) {
            return PolicyVerdict.DENY;
        }
        return PolicyVerdict.ALLOW;
    }

    @Override
    public void after(TurnContext ctx, Syscall syscall, SyscallResult result) {
        GuardHook.super.after(ctx, syscall, result);
    }
}
