package com.tepeu.session;

import java.time.Instant;
import java.util.Objects;

/** 人手动作的一条。不是对话 transcript。 */
public record AuditRecord(long seq, Instant at, String actor, String action, String detail) {

    public AuditRecord {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
    }
}
