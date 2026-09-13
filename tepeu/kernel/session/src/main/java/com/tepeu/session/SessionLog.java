package com.tepeu.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 事件日志。只追加。人手操作不进这里。
 * {@code attrs} 空合法，给工具名 / call id 这类对号键。
 */
public interface SessionLog {

    default long append(SessionEventType type, String body) {
        return append(type, body, Map.of());
    }

    long append(SessionEventType type, String body, Map<String, String> attrs);

    List<SessionEvent> readAll();

    Optional<SessionEvent> get(long seq);
}
