package com.tepeu.session;

import java.util.List;

/**
 * 人手动作：批准、拒绝。不进 {@link SessionLog}。
 */
public interface AuditSink {

    long record(String actor, String action, String detail);

    List<AuditRecord> readAll();
}
