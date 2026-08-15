package com.tepeu.os.kernel.session;

import java.util.List;
import java.util.Optional;

/**
 * 会话事实日志端口。
 */
public interface SessionLog {
    /** 追加事件，返回分配的序号。 */
    long append(SessionEventType type, String body, java.util.Map<String, String> attrs);

    /** 按序读取全部事件（投影用；生产实现可分页）。 */
    List<SessionEvent> readAll();

    Optional<SessionEvent> get(long seq);
}
