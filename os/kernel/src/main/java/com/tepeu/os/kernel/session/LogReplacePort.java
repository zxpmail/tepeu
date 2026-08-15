package com.tepeu.os.kernel.session;

import java.util.List;
import java.util.Optional;

/**
 * 日志区间替换端口 — 供 Compaction 写回摘要，非内核内嵌调 LLM。
 */
public interface LogReplacePort {
    /**
     * 用 checkpoint 事件替换 [fromSeq, toSeq] 闭区间的投影表面。
     * 底层仍保留可审计历史策略由 SessionStore 决定。
     */
    long replaceRange(long fromSeq, long toSeq, String checkpointBody);

    List<SessionEvent> surface();
}
