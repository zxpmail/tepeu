package com.tepeu.dispatch;

import com.tepeu.identity.InvokeContext;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;

/**
 * 一个名称一个处理函数。执行类失败走返回值（{@code ok=false}），不抛。
 * 不得返回 {@code null}——返回 null 由 dispatch 合成 HANDLER_ERROR。
 */
@FunctionalInterface
public interface Handler {

    SyscallResult handle(InvokeContext ctx, Syscall syscall);
}
