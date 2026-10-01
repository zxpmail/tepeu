package com.tepeu.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 领走一条收件箱消息的临时所有权。 */
public record ClaimLease(String claimId, String messageId, Instant expiresAt) {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(300);

    public ClaimLease {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
