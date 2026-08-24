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
 * 同工具同输入连续 {@link DoomLoop#THRESHOLD} 次 → 卫兵 NEED_APPROVAL（第三刀不直跑 handler）。
 * 须在 {@link SessionLoop} 落 TOOL_CALL 之后经总线 invoke；审批走 {@link com.tepeu.os.policy.ApprovalStore}。
 */
public final class DoomLoopGuardHook implements GuardHook {

    private final SessionStore sessions;

    public DoomLoopGuardHook(SessionStore sessions) {
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
        if (DoomLoop.tripped(session, syscall.name(), syscall.args())) {
            return PolicyVerdict.NEED_APPROVAL;
        }
        return PolicyVerdict.ALLOW;
    }

    @Override
    public void after(TurnContext ctx, Syscall syscall, SyscallResult result) {
        GuardHook.super.after(ctx, syscall, result);
    }
}
