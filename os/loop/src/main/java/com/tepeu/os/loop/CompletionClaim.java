package com.tepeu.os.loop;

/**
 * 完成声称。无新事件类型；证据只从现有 7 类 + ledger 还原。
 */
public enum CompletionClaim {
    /** 答复：声称之后须有非空 ASSISTANT_MESSAGE。 */
    REPLY,
    /** 工具：声称之后 TOOL_CALL 与 TOOL_RESULT 成对。 */
    TOOL_PAIR
}
