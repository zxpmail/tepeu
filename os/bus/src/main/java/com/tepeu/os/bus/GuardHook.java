package com.tepeu.os.bus;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;

/**
 * 卫兵钩：超时/取消/不变量/配额限流；异常即 fail-closed 拒绝。
 * ⏳ verdict 载体（deny>ask>allow 组合代数）挂账 kernel 端口演化。
 */
public interface GuardHook {
    /** 调用前；若取消或违规则抛 BusGuardException。 */
    void before(TurnContext ctx, Syscall syscall);

    /** 调用后观察（可选）。 */
    default void after(TurnContext ctx, Syscall syscall, SyscallResult result) {
    }
}
