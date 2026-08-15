package com.tepeu.os.kernel.bus;

import com.tepeu.os.kernel.context.TurnContext;

/**
 * 能力总线 — 注册/分发；入口钩 Policy + 卫兵（实现属②）。
 */
public interface CapabilityBus {
    /** 注册插头；同名覆盖策略由实现决定（默认禁止重复）。 */
    void register(String name, SyscallHandler handler);

    void setPolicyHook(PolicyHook policyHook);

    void addGuardHook(GuardHook guardHook);

    /**
     * 执行 syscall：卫兵 before → Policy → handler → 卫兵 after。
     */
    SyscallResult invoke(TurnContext ctx, Syscall syscall);
}
