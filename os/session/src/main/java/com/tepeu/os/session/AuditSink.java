package com.tepeu.os.session;

import java.util.List;
import java.util.Map;

/**
 * 人手旁路 / Slash 宿主副作用的审计真相。禁止写入冒充对话的会话事件。
 */
public interface AuditSink {
    default long record(String actor, String action, String detail) {
        return record(actor, action, detail, Map.of());
    }

    long record(String actor, String action, String detail, Map<String, String> attrs);

    List<AuditRecord> readAll();
}
