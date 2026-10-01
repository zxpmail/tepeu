package com.tepeu.session;

import com.tepeu.syscall.Usage;

import java.time.Instant;
import java.util.Objects;

/** 用量流水的一条。 */
public record LedgerEntry(long seq, Instant at, String syscallName, Usage usage) {

    public LedgerEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(usage, "usage");
    }
}
