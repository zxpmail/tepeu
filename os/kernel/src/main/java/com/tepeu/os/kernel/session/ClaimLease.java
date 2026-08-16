package com.tepeu.os.kernel.session;

import java.time.Instant;
import java.util.Objects;

/**
 * claim 租约 — 必带 TTL（ADR-016 第七轮：死租约可回收）；
 * 多副本时升级 fencing token（挂账远期）。
 */
public record ClaimLease(String claimId, String messageId, Instant expiresAt) {
    public ClaimLease {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
