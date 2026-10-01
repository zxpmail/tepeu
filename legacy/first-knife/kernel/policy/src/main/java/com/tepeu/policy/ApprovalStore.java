package com.tepeu.policy;

import com.tepeu.identity.InvokeContext;
import com.tepeu.syscall.Syscall;

import java.util.List;
import java.util.Optional;

/**
 * 审批端口。ask 登记（同键未决时幂等）；decide 落决策；consumeDecision 取走即消费。
 */
public interface ApprovalStore {

    /** 登记一次 asked，返回 approvalId。同（会话，名称，指纹）未决时幂等返回同一 id。 */
    String ask(InvokeContext ctx, Syscall syscall);

    /** 决策。审批须存在且未决；已决再决失败。 */
    void decide(String approvalId, boolean allow, String decidedBy);

    /** 取走该（会话，名称，指纹）的决策；未决或已消费则 empty。取走即消费。 */
    Optional<Boolean> consumeDecision(InvokeContext ctx, Syscall syscall);

    /** 按 id 读。没有则 empty。 */
    Optional<ApprovalRecord> get(String approvalId);

    /** 全部审批，按登记序。 */
    List<ApprovalRecord> records();
}
