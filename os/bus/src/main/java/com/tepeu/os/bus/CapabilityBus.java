package com.tepeu.os.bus;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.PolicyHook;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

/**
 * 能力总线（唯一门）— 注册/分发；入口检查：取消 → 卫兵 before → Policy（含同步重试式审批）
 * → handler → 卫兵 after。未装配 Policy / 审批通道即拒绝（fail-closed，C1/C2 第九轮）。
 */
public interface CapabilityBus {
    /** 注册 {@link SyscallHandler}；同名禁止重复。 */
    void register(String name, SyscallHandler handler);

    /** 装配 Policy（未装配即拒绝）。 */
    void setPolicyHook(PolicyHook policyHook);

    /** 装配审批通道（Policy 返回 NEED_APPROVAL 时必需；未装配即 fail-closed 拒绝）。 */
    void setApprovalStore(ApprovalStore approvalStore);

    void addGuardHook(GuardHook guardHook);

    /**
     * 已注册 syscall 名的确定性规范序（排序后的稳定列表）。
     * 顺序是缓存键与 llm.* 断言的组成部分；禁 HashMap 迭代序泄漏。
     */
    java.util.List<String> registeredSyscalls();

    SyscallResult invoke(TurnContext ctx, Syscall syscall);
}
