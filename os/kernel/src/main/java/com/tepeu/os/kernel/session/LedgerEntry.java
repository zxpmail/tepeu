package com.tepeu.os.kernel.session;

import com.tepeu.os.kernel.bus.Usage;

import java.time.Instant;
import java.util.Objects;

/**
 * 用量账本条目 — ledger 为 append-only 用量真相（三 store 之一，ADR-016 第五轮）。
 */
public record LedgerEntry(long seq, Instant at, String syscallName, Usage usage) {
    public LedgerEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(usage, "usage");
    }
}
