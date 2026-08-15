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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存能力总线 — 入口：卫兵 → Policy → handler。
 */
public final class InMemoryCapabilityBus implements CapabilityBus {

    private final Map<String, SyscallHandler> handlers = new ConcurrentHashMap<>();
    private final List<GuardHook> guards = new ArrayList<>();
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
    public synchronized void addGuardHook(GuardHook guardHook) {
        guards.add(Objects.requireNonNull(guardHook, "guardHook"));
    }

    @Override
    public SyscallResult invoke(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        if (ctx.isCancelled()) {
            throw new BusGuardException("turn cancelled");
        }
        for (GuardHook g : List.copyOf(guards)) {
            g.before(ctx, syscall);
        }
        PolicyVerdict verdict = policyHook.evaluate(ctx, syscall);
        if (verdict != PolicyVerdict.ALLOW) {
            throw new PolicyDeniedException(verdict, "policy=" + verdict + " syscall=" + syscall.name());
        }
        SyscallHandler handler = handlers.get(syscall.name());
        if (handler == null) {
            return SyscallResult.failure("NOT_FOUND", "no handler: " + syscall.name());
        }
        SyscallResult result = handler.handle(ctx, syscall);
        for (GuardHook g : List.copyOf(guards)) {
            g.after(ctx, syscall, result);
        }
        return result;
    }
}
