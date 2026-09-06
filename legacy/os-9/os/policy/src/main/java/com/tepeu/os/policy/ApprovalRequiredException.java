package com.tepeu.os.policy;

/**
 * 需审批（同步重试式 ask，C1 裁决 ADR-016 第九轮）：首次调用登记 asked 并抛出；
 * 决策者经 ApprovalStore.decide 后重试同一调用即得放行或拒绝。许可严格单次。
 */
public final class ApprovalRequiredException extends RuntimeException {
    private final String approvalId;
    private final String syscallName;

    public ApprovalRequiredException(String approvalId, String syscallName) {
        super("approval required: id=" + approvalId + " syscall=" + syscallName);
        this.approvalId = approvalId;
        this.syscallName = syscallName;
    }

    public String approvalId() {
        return approvalId;
    }

    public String syscallName() {
        return syscallName;
    }
}
