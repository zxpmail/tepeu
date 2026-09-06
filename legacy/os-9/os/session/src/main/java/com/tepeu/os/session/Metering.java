package com.tepeu.os.session;

/**
 * Metering 端口（内核必需端口之一）— 只供数，不产生裁决（ADR-016 第七轮）。
 * 开 turn 前由 Loop 问 {@link #withinBudget}；超限则不 claim。
 * 消耗从 {@link SessionLedger} 派生；预算上限属实现配置面。暂不拆独立 jar。
 */
public interface Metering {
    /** 开 turn 前预算门：本会话累计用量是否仍在预算内。实现只供数，不抛拒绝。 */
    boolean withinBudget(Session session);
}
