package com.tepeu.dispatch;

import com.tepeu.identity.InvokeContext;
import com.tepeu.policy.ApprovalStore;
import com.tepeu.policy.PolicyHook;
import com.tepeu.policy.PolicyVerdict;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次调用的唯一门。invoke 序：取消检查 → 授权 → 查表 → 调用。
 * 一切失败合成 {@link SyscallResult}，不抛异常：
 * {@link #CANCELLED} / {@link #DENIED} / {@link #APPROVAL_REQUIRED} / {@link #NOT_FOUND} / {@link #HANDLER_ERROR}。
 * 未装配授权按 DENIED（fail-closed）；策略返回词汇表外值也按 DENIED。
 * NEED_APPROVAL 先取既有决策（取走即消费），没有则登记新审批并回 APPROVAL_REQUIRED（output = approvalId）；
 * 决策后重试即放行或 DENIED，消费后再调须重新审批。
 */
public final class Dispatch {

    public static final String CANCELLED = "CANCELLED";
    public static final String DENIED = "DENIED";
    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String HANDLER_ERROR = "HANDLER_ERROR";

    private final PolicyHook policy;
    private final ApprovalStore approvals;
    private final Map<String, Handler> handlers = new HashMap<>();

    public Dispatch(PolicyHook policy, ApprovalStore approvals) {
        this.policy = policy;
        this.approvals = approvals;
    }

    /** 登记处理函数。同名重复登记失败。 */
    public void register(String name, Handler handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(name, handler) != null) {
            throw new IllegalStateException("already registered: " + name);
        }
    }

    /** 已注册名，字典序，不可变。 */
    public List<String> names() {
        return handlers.keySet().stream().sorted().toList();
    }

    /** 一次调用。失败合成结果，不抛。 */
    public SyscallResult invoke(InvokeContext ctx, String name, Map<String, String> args) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(name, "name");
        if (ctx.isCancelled()) {
            return SyscallResult.failure(CANCELLED, name);
        }
        if (policy == null) {
            return SyscallResult.failure(DENIED, "policy not installed");
        }
        Syscall syscall = new Syscall(name, args);
        PolicyVerdict verdict = policy.evaluate(ctx, syscall);
        if (verdict == PolicyVerdict.NEED_APPROVAL) {
            if (approvals == null) {
                return SyscallResult.failure(DENIED, "approval channel not installed");
            }
            Boolean allow = approvals.consumeDecision(ctx, syscall).orElse(null);
            if (allow == null) {
                return SyscallResult.failure(APPROVAL_REQUIRED, approvals.ask(ctx, syscall));
            }
            if (!allow) {
                return SyscallResult.failure(DENIED, name);
            }
        } else if (verdict != PolicyVerdict.ALLOW) {
            return SyscallResult.failure(DENIED, name);
        }
        Handler handler = handlers.get(name);
        if (handler == null) {
            return SyscallResult.failure(NOT_FOUND, name);
        }
        try {
            return handler.handle(ctx, syscall);
        } catch (RuntimeException e) {
            return SyscallResult.failure(HANDLER_ERROR, e.getClass().getSimpleName());
        }
    }
}
