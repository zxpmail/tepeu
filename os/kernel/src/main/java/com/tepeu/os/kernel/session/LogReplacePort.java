package com.tepeu.os.kernel.session;

import java.util.List;

/**
 * 日志区间替换端口 — 供 Compaction 写回摘要，非内核内嵌调 LLM（红线 §6-5）。
 * surface 代数：不删事件，checkpoint 插区间位（ADR-016 第三轮）。
 */
public interface LogReplacePort {
    /**
     * 用 checkpoint 事件替换 [fromSeq, toSeq] 闭区间的投影表面。
     * 区间校验：from > 0 且 from <= to，否则拒绝；底层审计历史保留。
     */
    long replaceRange(long fromSeq, long toSeq, String checkpointBody);

    /** 模型读面（surface）；人类 transcript 读 append-origin 全量（双读者）。 */
    List<SessionEvent> surface();
}
