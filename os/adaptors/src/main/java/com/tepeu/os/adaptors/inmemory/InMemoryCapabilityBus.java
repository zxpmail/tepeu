package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.bus.ApprovalRequiredException;
import com.tepeu.os.kernel.bus.ApprovalStore;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单机内存能力总线 — 入口：取消 → 卫兵 before → Policy（含同步重试式审批）→ handler → 卫兵 after。
 * C2 fail-closed（第九轮）：未装配 Policy、或 NEED_APPROVAL 而未装配审批通道，一律拒绝；
 * Policy/卫兵钩子异常一律规范化为拒绝，禁异常穿透（ADR-016 第三轮）。
 * 失败双通道（第九轮 C3）：拦截类走异常（BusGuard/PolicyDenied/ApprovalRequired，catch 方=调用方）；
 * 执行类不抛穿（ok=false + errorCode）。
 */
public final class InMemoryCapabilityBus implements CapabilityBus {

    private final Map<String, SyscallHandler> handlers = new ConcurrentHashMap<>();
    private final List<GuardHook> guards = new CopyOnWriteArrayList<>();
    private volatile PolicyHook policyHook;
    private volatile ApprovalStore approvalStore;

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
    public void setApprovalStore(ApprovalStore approvalStore) {
        this.approvalStore = Objects.requireNonNull(approvalStore, "approvalStore");
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
        if (policyHook == null) {
            // C2：未装配 Policy 即拒绝（fail-closed），无默认放行
            throw new PolicyDeniedException(PolicyVerdict.DENY,
                    "policy hook not installed (fail-closed): syscall=" + syscall.name());
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
        if (verdict == PolicyVerdict.DENY) {
            throw new PolicyDeniedException(verdict, "policy=" + verdict + " syscall=" + syscall.name());
        }
        if (verdict == PolicyVerdict.NEED_APPROVAL) {
            ApprovalStore store = approvalStore;
            if (store == null) {
                // C2：需审批而无审批通道 = 拒绝（fail-closed）
                throw new PolicyDeniedException(PolicyVerdict.NEED_APPROVAL,
                        "approval required but no approval channel installed (fail-closed): syscall="
                                + syscall.name());
            }
            // C1 同步重试式：先消费既有决策；无决策则登记 asked 并抛出，由决策者 decide 后重试
            Optional<Boolean> decision = store.consumeDecision(ctx, syscall);
            if (decision.isEmpty()) {
                throw new ApprovalRequiredException(store.ask(ctx, syscall), syscall.name());
            }
            if (!decision.get()) {
                throw new PolicyDeniedException(PolicyVerdict.NEED_APPROVAL,
                        "approval decided: deny syscall=" + syscall.name());
            }
            // 决策=放行 → 落 handler（许可已消费，下次同调用重新走审批）
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
