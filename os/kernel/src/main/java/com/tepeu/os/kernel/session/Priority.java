package com.tepeu.os.kernel.session;

/**
 * Inbox 消息优先级（ADR-016 第四轮）：NOW 级运行中到达即抢占当前流——
 * 抢占的执行语义属 ③ Loop（只切流式 chunk 边界）；内核只保证领取顺序 NOW > NEXT > LATER。
 */
public enum Priority {
    NOW,
    NEXT,
    LATER
}
