package com.tepeu.os.kernel.session;

/**
 * 会话事件类型 — 最小集；可扩展。
 * 词汇表由 SessionConformance 钉死（manifest 数量测试，ADR-016 第六/八轮）：
 * 新增/删除必须显式改 conformance 用例与 ADR。SYSTEM_NOTE 已砍除（第八轮：语义未定义者不入词汇表）。
 */
public enum SessionEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    REASONING,
    PLAN_STEP,
    COMPACTION_CHECKPOINT
}
