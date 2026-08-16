package com.tepeu.os.kernel.bus;

import com.tepeu.os.kernel.context.TurnContext;

/**
 * 工具/执行插头 — 彼此禁止互引，只经总线调度（红线 §6-2）。
 */
@FunctionalInterface
public interface SyscallHandler {
    SyscallResult handle(TurnContext ctx, Syscall syscall);
}
