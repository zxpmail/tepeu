package com.tepeu.os.identity;

/**
 * Agent 种类 — 落在 {@link Principal} 上；默认 {@link #PERSONAL}。
 * {@link #ENTERPRISE}/{@link #ROLE}/{@link #TASK} 为词汇预留，企业切片未开时勿当已交付能力。
 */
public enum AgentKind {
    /** 个人主体（本机默认）。 */
    PERSONAL,
    /** 企业主体（预留）。 */
    ENTERPRISE,
    /** 角色主体（预留）。 */
    ROLE,
    /** 任务主体（预留）。 */
    TASK
}
