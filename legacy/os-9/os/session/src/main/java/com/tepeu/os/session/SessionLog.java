package com.tepeu.os.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话事实日志端口（entries）— 模型可见 ⇔ 可还原的对话真相。
 * seq 由 append 点分配，本日志内严格单调连续且唯一；append 点 lossless（红线 §9）。
 * 禁明文 secret；人手操作不进此端口（见 {@link AuditSink}）。
 */
public interface SessionLog {
    /** 追加事件，返回分配的序号。 */
    long append(SessionEventType type, String body, Map<String, String> attrs);

    /** 按序读取全部事件（投影用；生产实现可分页）。 */
    List<SessionEvent> readAll();

    Optional<SessionEvent> get(long seq);
}
