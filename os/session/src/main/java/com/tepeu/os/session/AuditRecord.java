package com.tepeu.os.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 人手宿主操作审计 — 不进会话 entries（双真相，ADR-016）。
 */
public record AuditRecord(
        long seq,
        Instant at,
        String actor,
        String action,
        String detail,
        Map<String, String> attrs) {

    public AuditRecord {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }
}
