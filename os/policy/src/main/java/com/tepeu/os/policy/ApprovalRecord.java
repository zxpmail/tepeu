package com.tepeu.os.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 审批记录 — asked/decided 事件对（ADR-016 第三轮；证据须持久，生产默认 SQLite）。
 */
public record ApprovalRecord(
        String approvalId,
        String sessionId,
        String syscallName,
        Instant askedAt,
        Optional<Instant> decidedAt,
        Optional<Boolean> allow,
        Optional<String> decidedBy) {
    public ApprovalRecord {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(askedAt, "askedAt");
        decidedAt = decidedAt == null ? Optional.empty() : decidedAt;
        allow = allow == null ? Optional.empty() : allow;
        decidedBy = decidedBy == null ? Optional.empty() : decidedBy;
    }

    public boolean decided() {
        return decidedAt.isPresent() && allow.isPresent();
    }
}
