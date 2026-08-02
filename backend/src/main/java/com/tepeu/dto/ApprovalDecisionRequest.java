package com.tepeu.dto;

/**
 * 工具审批裁决请求体。
 * 关联：ApprovalController。
 */
public class ApprovalDecisionRequest {

    /** "approve" 或 "deny" */
    private String decision;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
