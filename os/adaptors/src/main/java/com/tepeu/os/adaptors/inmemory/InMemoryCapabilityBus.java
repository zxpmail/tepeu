package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.bus.BusGuardException;
import com.tepeu.os.kernel.bus.CapabilityBus;
import com.tepeu.os.kernel.bus.GuardHook;
import com.tepeu.os.kernel.bus.PolicyDeniedException;
import com.tepeu.os.kernel.bus.PolicyHook;
import com.tepeu.os.kernel.bus.PolicyVerdict;
import com.tepeu.os.kernel.bus.Syscall;
import com.tepeu.os.kernel.bus.SyscallHandler;
import com.tepeu.os.kernel.bus.SyscallResult;
import com.tepeu.os.kernel.context.TurnContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单机内存能力总线 — 入口：卫兵 → Policy → handler。
 * fail-closed：Policy/卫兵钩子异常一律规范化为拒绝，禁异常穿透（ADR-016 dsh 对账裁决 4）。
 */
public final class InMemoryCapabilityBus implements CapabilityBus {

    private final Map<String, SyscallHandler> handlers = new ConcurrentHashMap<>();
    private final List<GuardHook> guards = new CopyOnWriteArrayList<>();
    private volatile PolicyHook policyHook = (ctx, syscall) -> PolicyVerdict.ALLOW;

    @Override
    public void register(String name, SyscallHandler handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(name, handler) != null) {
            throw new IllegalStateException("syscall already registered: " + name);
        }
    }

    @Override
    public void setPolicyHook(PolicyHook policyHook) {
        this.policyHook = Objects.requireNonNull(policyHook, "policyHook");
    }

    @Override
    public void addGuardHook(GuardHook guardHook) {
        guards.add(Objects.requireNonNull(guardHook, "guardHook"));
    }

    @Override
    public SyscallResult invoke(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        if (ctx.isCancelled()) {
            throw new BusGuardException("turn cancelled");
        }
        for (GuardHook g : guards) {
            try {
                g.before(ctx, syscall);
            } catch (BusGuardException e) {
                throw e;
            } catch (RuntimeException e) {
                // fail-closed：卫兵自身故障按拒绝处理，不穿透
                throw new BusGuardException("guard failed (fail-closed): " + e);
            }
        }
        PolicyVerdict verdict;
        try {
            verdict = policyHook.evaluate(ctx, syscall);
        } catch (RuntimeException e) {
            // fail-closed：策略钩子故障 = 拒绝
            throw new PolicyDeniedException(PolicyVerdict.DENY,
                    "policy hook failed (fail-closed): " + e);
        }
        if (verdict == null) {
            throw new PolicyDeniedException(PolicyVerdict.DENY,
                    "policy hook returned null (fail-closed): syscall=" + syscall.name());
        }
        if (verdict != PolicyVerdict.ALLOW) {
            throw new PolicyDeniedException(verdict, "policy=" + verdict + " syscall=" + syscall.name());
        }
        SyscallHandler handler = handlers.get(syscall.name());
        if (handler == null) {
            return SyscallResult.failure("NOT_FOUND", "no handler: " + syscall.name());
        }
        SyscallResult result;
        try {
            result = handler.handle(ctx, syscall);
        } catch (RuntimeException e) {
            // 失败可见：工具错误是结果字段，不因程序失败抛穿总线
            result = SyscallResult.failure("HANDLER_ERROR", String.valueOf(e));
        }
        if (result == null) {
            result = SyscallResult.failure("HANDLER_ERROR", "handler returned null");
        }
        for (GuardHook g : guards) {
            try {
                g.after(ctx, syscall, result);
            } catch (BusGuardException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new BusGuardException("guard after failed (fail-closed): " + e);
            }
        }
        return result;
    }
}
