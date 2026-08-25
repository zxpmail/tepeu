package com.tepeu.os.syscall;

import com.tepeu.os.identity.TurnContext;

/**
 * 工具 / 执行插头 — {@code (TurnContext, Syscall) → SyscallResult}。
 * <p>
 * 红线（底板 §6-2）：handler 彼此禁止互引，只经能力总线调度；不在此接口上挂业务状态。
 */
@FunctionalInterface
public interface SyscallHandler {
    SyscallResult handle(TurnContext ctx, Syscall syscall);
}
