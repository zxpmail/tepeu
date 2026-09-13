package com.tepeu.policy;

import com.tepeu.identity.InvokeContext;
import com.tepeu.syscall.Syscall;

/**
 * 授权判定口。由装配方注入；未装配时调用方按 {@code DENY} 处理。
 */
@FunctionalInterface
public interface PolicyHook {

    PolicyVerdict evaluate(InvokeContext ctx, Syscall syscall);
}
