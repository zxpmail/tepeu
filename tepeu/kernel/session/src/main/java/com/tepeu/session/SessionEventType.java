package com.tepeu.session;

/** 事件日志词汇。第一刀无压缩、无 fork。
 * @author zxpma*/
public enum SessionEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_CALL,
    TOOL_RESULT,
    REASONING,
    PLAN_STEP
}
