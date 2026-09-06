package com.tepeu.os.session;

import com.tepeu.os.syscall.Usage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 用量账本条目 — ledger 为 append-only 用量真相（三 store 之一，ADR-016 第五轮）。
 * attrs 承载派生断言 digest/版本号等（第十轮）；不是第四个 store。
 */
public record LedgerEntry(long seq, Instant at, String syscallName, Usage usage, Map<String, String> attrs) {
    public LedgerEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(usage, "usage");
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }

    public LedgerEntry(long seq, Instant at, String syscallName, Usage usage) {
        this(seq, at, syscallName, usage, Map.of());
    }
}
