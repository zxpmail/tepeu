package com.tepeu.os.loop;

/**
 * 完成声称。证据从模型可见事件 + ledger 还原；END_SEED 不是完成证据。
 */
public enum CompletionClaim {
    /** 答复：声称之后须有非空 ASSISTANT_MESSAGE。 */
    REPLY,
    /** 工具：声称之后 TOOL_CALL 与 TOOL_RESULT 成对。 */
    TOOL_PAIR
}
