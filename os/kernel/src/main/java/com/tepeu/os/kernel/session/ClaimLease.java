package com.tepeu.os.kernel.session;

import java.time.Instant;
import java.util.Objects;

/**
 * claim 租约 — 多副本时由 InboxClaim 适配器实现抢占。
 */
public record ClaimLease(String claimId, String messageId, Instant expiresAt) {
    public ClaimLease {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
