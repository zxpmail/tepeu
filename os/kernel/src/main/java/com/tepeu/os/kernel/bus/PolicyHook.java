package com.tepeu.os.kernel.bus;

import com.tepeu.os.kernel.context.TurnContext;

/**
 * 总线入口 Policy 钩 — 实现在适配环。
 */
@FunctionalInterface
public interface PolicyHook {
    PolicyVerdict evaluate(TurnContext ctx, Syscall syscall);
}
