package com.tepeu.os.policy;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

import java.util.List;
import java.util.Optional;

/**
 * 审批端口（内核必需端口之一，与 Policy 同行）— asked/decided 事件对 + 严格单次许可。
 * 同步重试式 ask（C1，ADR-016 第九轮）：ask 登记并抛 ApprovalRequiredException；
 * decide 落决策；consumeDecision 取走即消费（单次）。
 */
public interface ApprovalStore {
    /** 登记一次 asked，返回 approvalId（同一 (session, syscall, argsDigest) 未决时幂等返回同一 id）。 */
    String ask(TurnContext ctx, Syscall syscall);

    /** 决策（必须在 asked 之后；由宿主/审批 UI 调用）。 */
    void decide(String approvalId, boolean allow, String decidedBy);

    /** 取走该 (session, syscall, argsDigest) 的最新决策（若有）；严格单次——取走即消费。 */
    Optional<Boolean> consumeDecision(TurnContext ctx, Syscall syscall);

    /** 按 id 读取；不存在则 empty。 */
    Optional<ApprovalRecord> get(String approvalId);

    /** 审计视图：全部 asked/decided 记录。 */
    List<ApprovalRecord> records();
}
