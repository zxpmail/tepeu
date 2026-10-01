package com.tepeu.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 一条审批：asked 登记到 decided。绑定（会话，名称，args 指纹），不单绑名称。
 */
public record ApprovalRecord(
        String approvalId,
        String sessionId,
        String syscallName,
        String argsDigest,
        Instant askedAt,
        Optional<Instant> decidedAt,
        Optional<Boolean> allow,
        Optional<String> decidedBy,
        Optional<Instant> consumedAt) {
    public ApprovalRecord {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(argsDigest, "argsDigest");
        Objects.requireNonNull(askedAt, "askedAt");
        decidedAt = decidedAt == null ? Optional.empty() : decidedAt;
        allow = allow == null ? Optional.empty() : allow;
        decidedBy = decidedBy == null ? Optional.empty() : decidedBy;
        consumedAt = consumedAt == null ? Optional.empty() : consumedAt;
    }

    public boolean decided() {
        return decidedAt.isPresent() && allow.isPresent();
    }

    /** 许可严格单次：取走即消费。 */
    public boolean consumed() {
        return consumedAt.isPresent();
    }
}
