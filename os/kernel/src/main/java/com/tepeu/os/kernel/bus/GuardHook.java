package com.tepeu.os.kernel.bus;

import com.tepeu.os.kernel.context.TurnContext;

/**
 * 卫兵钩：超时/取消/不变量；实现可组合。
 */
public interface GuardHook {
    /** 调用前；若取消或违规则抛 BusGuardException。 */
    void before(TurnContext ctx, Syscall syscall);

    /** 调用后观察（可选）。 */
    default void after(TurnContext ctx, Syscall syscall, SyscallResult result) {
    }
}
