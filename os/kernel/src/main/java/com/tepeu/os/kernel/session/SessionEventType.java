package com.tepeu.os.kernel.session;

/**
 * 会话事件类型 — 最小集；可扩展。
 */
public enum SessionEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    REASONING,
    PLAN_STEP,
    SYSTEM_NOTE,
    COMPACTION_CHECKPOINT
}
