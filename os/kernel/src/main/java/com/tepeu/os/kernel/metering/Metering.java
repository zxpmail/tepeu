package com.tepeu.os.kernel.metering;

import com.tepeu.os.kernel.session.Session;

/**
 * Metering 端口（内核必需端口之一）— 供数 + 与 Policy 协作拦截预算；
 * Metering 自身不产生裁决（ADR-016 第七轮正典表述）。
 * 消耗从会话 ledger 派生；预算上限属实现配置面。
 */
public interface Metering {
    /** 开 turn 前预算门：本会话累计用量是否仍在预算内。 */
    boolean withinBudget(Session session);
}
