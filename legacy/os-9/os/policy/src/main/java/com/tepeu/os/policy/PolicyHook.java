package com.tepeu.os.policy;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

/**
 * 总线入口 Policy 钩 — 实现在适配环；未装配即拒绝（C2，ADR-016 第九轮）。
 */
@FunctionalInterface
public interface PolicyHook {
    PolicyVerdict evaluate(TurnContext ctx, Syscall syscall);
}
