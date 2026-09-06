package com.tepeu.os.bus;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;

/**
 * 卫兵钩：超时/取消/不变量/配额限流。
 * before 返回封闭 union；总线聚合 deny &gt; ask &gt; allow。异常即 fail-closed 拒绝。
 */
@FunctionalInterface
public interface GuardHook {
    PolicyVerdict before(TurnContext ctx, Syscall syscall);

    /** 调用后观察（可选）。 */
    default void after(TurnContext ctx, Syscall syscall, SyscallResult result) {
    }
}
