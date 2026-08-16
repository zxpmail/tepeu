package com.tepeu.os.kernel.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话事实日志端口（entries）— seq 由 append 点分配、本日志内严格单调连续且唯一；
 * append 点做 lossless 校验（红线 §9）。
 */
public interface SessionLog {
    /** 追加事件，返回分配的序号。 */
    long append(SessionEventType type, String body, Map<String, String> attrs);

    /** 按序读取全部事件（投影用；生产实现可分页）。 */
    List<SessionEvent> readAll();

    Optional<SessionEvent> get(long seq);
}
