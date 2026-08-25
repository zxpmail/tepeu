package com.tepeu.os.session;

/**
 * 会话事件类型 — 最小集。
 * 词汇表由 session 测试 conformance 钉死（manifest 数量测试，ADR-016 第六/八轮）：
 * 新增/删除必须显式改 conformance 用例与 ADR。语义未定义者不入词汇表（SYSTEM_NOTE 教训）。
 */
public enum SessionEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    REASONING,
    PLAN_STEP,
    COMPACTION_CHECKPOINT,
    /** fork/resume 种子边界；审计日志有、模型面（surface）无。 */
    END_SEED
}
