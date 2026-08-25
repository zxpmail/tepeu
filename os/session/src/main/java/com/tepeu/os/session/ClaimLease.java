package com.tepeu.os.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * claim 租约 — 「领走」Inbox 一条消息的临时所有权；必带 TTL（死租约可回收）。
 * 多副本时升级 fencing token/steal（挂账）；本记录本身不含 fence。
 */
public record ClaimLease(String claimId, String messageId, Instant expiresAt) {

    /** 本机默认租约时长；memory / sqlite 实现与 conformance 共用此值。 */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(300);

    public ClaimLease {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
