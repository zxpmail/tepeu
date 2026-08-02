package com.tepeu.dto;

/**
 * 更新工作区预算请求。
 * 关联：BudgetController。
 */
public class BudgetUpdateRequest {

    /** 预算上限（USD）；null=不限制；0=零预算 */
    private Double budgetUsd;
    /** 是否硬门禁（超预算阻断新对话） */
    private Boolean hardLimit;
    /** 告警比例 0–1，默认 0.8 */
    private Double alertThreshold;

    public Double getBudgetUsd() {
        return budgetUsd;
    }

    public void setBudgetUsd(Double budgetUsd) {
        this.budgetUsd = budgetUsd;
    }

    public Boolean getHardLimit() {
        return hardLimit;
    }

    public void setHardLimit(Boolean hardLimit) {
        this.hardLimit = hardLimit;
    }

    public Double getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(Double alertThreshold) {
        this.alertThreshold = alertThreshold;
    }
}
